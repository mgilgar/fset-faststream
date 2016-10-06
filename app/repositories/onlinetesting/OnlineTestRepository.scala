/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package repositories.onlinetesting

import factories.DateTimeFactory
import model.ApplicationStatus.ApplicationStatus
import model.Exceptions.{ CannotFindTestByCubiksId, UnexpectedException }
import org.joda.time.DateTime
import model.OnlineTestCommands.OnlineTestApplication
import model.persisted.{ Test, _ }
import model.ProgressStatuses.{ ProgressStatus, TestProgress }
import model._
import play.api.Logger
import reactivemongo.bson._
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

abstract class OnlineTestRepository extends RandomSelection {
  this: ReactiveRepository[_, _] =>

  type U <: Test
  type T <: TestProfile[U]

  val testProgress: TestProgress

  case class PhaseTestProfileWithAppId(applicationId: String, phaseTestProfile: T)

  val thisApplicationStatus: ApplicationStatus
  val phaseName: String
  val dateTimeFactory: DateTimeFactory

  implicit val testBsonHandler: BSONDocumentReader[T] with BSONDocumentWriter[T] with BSONHandler[BSONDocument, T]
  implicit val testWithIdBsonHandler = Macros.handler[PhaseTestProfileWithAppId]

  def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]]

  def getTestGroup(applicationId: String): Future[Option[T]] = {
    getTestGroup(applicationId, phaseName)
  }

  def getTestProfileByToken(token: String): Future[T] = {
    getTestProfileByToken(token, phaseName)
  }

  def getTestGroup(applicationId: String, phase: String = phaseName): Future[Option[T]] = {
    val query = BSONDocument.apply("applicationId".->(applicationId))
    phaseTestProfileByQuery(query, phase)
  }

  def getTestProfileByToken(token: String, phase: String = phaseName): Future[T] = {
    val query = BSONDocument.apply(StringContext.apply("testGroups.", ".tests").s(phase).->(BSONDocument.apply(
      "$elemMatch" -> BSONDocument.apply("token" -> token)
    )))

    phaseTestProfileByQuery(query).map { x =>
      x.getOrElse(cannotFindTestByToken(token))
    }
  }

  def cannotFindTestByCubiksId(cubiksUserId: Int) = {
    throw CannotFindTestByCubiksId.apply(StringContext.apply("Cannot find test group by cubiks Id: ", "").s(cubiksUserId))
  }

  def cannotFindTestByToken(token: String) = {
    throw CannotFindTestByCubiksId.apply(StringContext.apply("Cannot find test group by token: ", "").s(token))
  }

  private def phaseTestProfileByQuery(query: BSONDocument, phase: String = phaseName): Future[Option[T]] = {
    val projection = BSONDocument.apply(StringContext.apply("testGroups.", "").s(phase) -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(doc) =>
        val bson = doc.getAs[BSONDocument]("testGroups").map(_.getAs[BSONDocument](phase).get)
        bson.map(x => testBsonHandler.read(x))
      case _ => None
    }
  }

  def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime, phase: String = phaseName): Future[Unit] = {
    val query = BSONDocument.apply("applicationId" -> applicationId)

    collection.update(query, BSONDocument.apply("$set" -> BSONDocument.apply(
      StringContext.apply("testGroups.", ".expirationDate").s(phase) -> expirationDate
    ))).map { status =>
      if (status.n != 1) {
        val msg = StringContext.apply("Query to update testgroup expiration affected ", " rows instead of 1! (App Id: ", ")").s(status.n, applicationId)
        Logger.warn(msg)
        throw UnexpectedException.apply(msg)
      }
      ()
    }
  }

  def nextExpiringApplication(progressStatusQuery: BSONDocument, phase: String = phaseName): Future[Option[ExpiringOnlineTest]] = {
    val query = BSONDocument.apply("$and" -> BSONArray.apply(
      BSONDocument.apply(
        "applicationStatus" -> thisApplicationStatus
      ),
      BSONDocument.apply(
        StringContext.apply("testGroups.", ".expirationDate").s(phase) -> BSONDocument.apply("$lte" -> dateTimeFactory.nowLocalTimeZone) // Serialises to UTC.
      ), progressStatusQuery))

    selectRandom(query).map(_.map(ExpiringOnlineTest.fromBson))
  }

  def nextTestForReminder(reminder: ReminderNotice, phase: String = phaseName,
    progressStatusQuery: BSONDocument
  ): Future[Option[NotificationExpiringOnlineTest]] = {
    val query = BSONDocument.apply("$and" -> BSONArray.apply(
      BSONDocument.apply("applicationStatus" -> thisApplicationStatus),
      BSONDocument.apply(StringContext.apply("testGroups.", ".expirationDate").s(phase) ->
        BSONDocument.apply( "$lte" -> dateTimeFactory.nowLocalTimeZone.plusHours(reminder.hoursBeforeReminder)) // Serialises to UTC.
      ),
      progressStatusQuery
    ))

    selectRandom(query).map(_.map(NotificationExpiringOnlineTest.fromBson))
  }


  def updateProgressStatus(appId: String, progressStatus: ProgressStatus): Future[Unit] = {
    require(progressStatus.applicationStatus == thisApplicationStatus, "Forbidden progress status update")

    val query = BSONDocument.apply(
      "applicationId" -> appId,
      "applicationStatus" -> thisApplicationStatus
    )

    val applicationStatusBSON = BSONDocument.apply("$set" -> BSONDocument.apply(
      StringContext.apply("progress-status.", "").s(progressStatus) -> true
    ))
    collection.update(query, applicationStatusBSON, upsert = false) map ( _ => () )
  }

  def removeTestProfileProgresses(appId: String, progressStatuses: List[ProgressStatus]): Future[Unit] = {
    require(progressStatuses.nonEmpty)
    require(progressStatuses forall (_.applicationStatus == thisApplicationStatus), StringContext.apply("Cannot remove non ", " progress status").s(phaseName))

    val query = BSONDocument.apply(
      "applicationId" -> appId,
      "applicationStatus" -> thisApplicationStatus
    )
    val progressesToRemoveQueryPartial = progressStatuses map (p => StringContext.apply("progress-status.", "").s(p) -> BSONString.apply(""))

    val updateQuery = BSONDocument.apply("$unset" -> BSONDocument.apply(progressesToRemoveQueryPartial))

    collection.update(query, updateQuery, upsert = false) map ( _ => () )
  }


  def insertTestResult(appId: String, test: CubiksTest, testResult: TestResult): Future[Unit] = {
    val query = BSONDocument.apply(
      "applicationId" -> appId,
      StringContext.apply("testGroups.", ".tests").s(phaseName) -> BSONDocument.apply(
        "$elemMatch" -> BSONDocument.apply("cubiksUserId" -> test.cubiksUserId)
      )
    )

    val update = BSONDocument.apply("$set" -> BSONDocument.apply(
      StringContext.apply("testGroups.", ".tests.", "$", ".testResult").s(phaseName) -> TestResult.testResultBsonHandler.write(testResult)
    ))

    collection.update(query, update, upsert = false) map( _ => () )
  }


  def insertOrUpdateTestGroup(applicationId: String, testProfile: T) = {
    val query = BSONDocument.apply("applicationId" -> applicationId)

    val applicationStatusBSON = BSONDocument.apply("$set" -> BSONDocument.apply(
      StringContext.apply("progress-status.", "").s(testProgress.INVITED) -> true,
      "applicationStatus" -> testProgress.applicationStatus
    )) ++ BSONDocument.apply("$set" -> BSONDocument.apply(
      "testGroups" -> BSONDocument(phaseName -> testProfile)
    ))

    collection.update(query, applicationStatusBSON, upsert = false) map { status =>
      if (status.n != 1) {
        val msg = StringContext.apply("", " rows affected when inserting or updating instead of 1! (App Id: ", ")").s(status.n, applicationId)
        Logger.warn(msg)
        throw UnexpectedException.apply(msg)
      }
      ()
    }
  }

  def nextTestGroupWithReportReady: Future[Option[PhaseTestProfileWithAppId]] = {
    val query = BSONDocument.apply("$and" -> BSONArray.apply(
      BSONDocument.apply("applicationStatus" -> thisApplicationStatus),
      BSONDocument.apply(StringContext.apply("progress-status.", "").s(testProgress.RESULTS_READY) -> true),
      BSONDocument.apply(StringContext.apply("progress-status.", "").s(testProgress.RESULTS_RECEIVED) ->
        BSONDocument.apply("$ne" -> true)
      )
    ))

    selectRandom(query).map(_.map { doc =>
      val group = doc.getAs[BSONDocument]("testGroups").get.getAs[BSONDocument](phaseName).get
      PhaseTestProfileWithAppId.apply(
        applicationId = doc.getAs[String]("applicationId").get,
        testBsonHandler.read(group)
      )
    })
  }

  def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime): Future[Unit] = {
    updateGroupExpiryTime(applicationId, expirationDate, phaseName)
  }

  def nextExpiringApplication: Future[Option[ExpiringOnlineTest]] = {
    val progressStatusQuery = BSONDocument.apply("$and" -> BSONArray.apply(
      BSONDocument.apply(StringContext.apply("progress-status.", "").s(testProgress.COMPLETED) -> BSONDocument.apply("$ne" -> true)),
      BSONDocument.apply(StringContext.apply("progress-status.", "").s(testProgress.EXPIRED) -> BSONDocument.apply("$ne" -> true))
    ))

    nextExpiringApplication(progressStatusQuery, phaseName)
  }

  def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]] = {
    val progressStatusQuery = BSONDocument.apply("$and" -> BSONArray.apply(
      BSONDocument.apply(StringContext.apply("progress-status.", "").s(testProgress.COMPLETED) -> BSONDocument.apply("$ne" -> true)),
      BSONDocument.apply(StringContext.apply("progress-status.", "").s(testProgress.EXPIRED) -> BSONDocument.apply("$ne" -> true)),
      BSONDocument.apply(StringContext.apply("progress-status.", "").s(reminder.progressStatuses) -> BSONDocument.apply("$ne" -> true))
    ))

    nextTestForReminder(reminder, phaseName, progressStatusQuery)
  }

  def getTestProfileByCubiksId(cubiksUserId: Int): Future[PhaseTestProfileWithAppId] = {
    val query = BSONDocument.apply(StringContext.apply("testGroups.", ".tests").s(phaseName) -> BSONDocument.apply(
      "$elemMatch" -> BSONDocument.apply("cubiksUserId" -> cubiksUserId)
    ))
    val projection = BSONDocument.apply("applicationId" -> 1, StringContext.apply("testGroups.", "").s(phaseName) -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(doc) =>
        val applicationId = doc.getAs[String]("applicationId").get
        val bsonPhase = doc.getAs[BSONDocument]("testGroups").map(_.getAs[BSONDocument](phaseName).get)
        val phaseTestGroup = bsonPhase.map(testBsonHandler.read).getOrElse(cannotFindTestByCubiksId(cubiksUserId))
        PhaseTestProfileWithAppId.apply(applicationId, phaseTestGroup)
      case _ => cannotFindTestByCubiksId(cubiksUserId)
    }
  }
}
