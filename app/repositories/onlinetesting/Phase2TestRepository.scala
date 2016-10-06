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
import model.Exceptions.UnexpectedException
import org.joda.time.DateTime
import model.persisted.{ CubiksTest, Phase2TestProfile }
import model.persisted.{ ExpiringOnlineTest, NotificationExpiringOnlineTest, Phase2TestProfileWithAppId, TestResult }
import model.ProgressStatuses.{ PHASE1_TESTS_INVITED, _ }
import model.{ ApplicationStatus, ProgressStatuses, ReminderNotice }
import play.api.Logger
import reactivemongo.api.DB
import reactivemongo.bson._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Phase2TestRepository extends OnlineTestRepository[CubiksTest, Phase2TestProfile] {
  this: ReactiveRepository[_, _] =>

  def getTestGroup(applicationId: String): Future[Option[Phase2TestProfile]]

  def getTestProfileByToken(token: String): Future[Phase2TestProfile]

  def getTestProfileByCubiksId(cubiksUserId: Int): Future[Phase2TestProfileWithAppId]

  def insertOrUpdateTestGroup(applicationId: String, phase2TestProfile: Phase2TestProfile): Future[Unit]

  def nextTestGroupWithReportReady: Future[Option[Phase2TestProfileWithAppId]]

  def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime): Future[Unit]

  def removeTestProfileProgresses(appId: String, progressStatuses: List[ProgressStatus]): Future[Unit]

  def insertTestResult(appId: String, phase2Test: CubiksTest, testResult: TestResult): Future[Unit]

  def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]]

  def nextExpiringApplication: Future[Option[ExpiringOnlineTest]]
}

class Phase2TestMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[Phase2TestProfile, BSONObjectID]("application", mongo,
    model.persisted.Phase2TestProfile.phase2TestProfileFormat, ReactiveMongoFormats.objectIdFormats
  ) with Phase2TestRepository {

  val phaseName = "PHASE2"
  val thisApplicationStatus: ApplicationStatus = ApplicationStatus.PHASE2_TESTS
  val dateTimeFactory = dateTime

  override implicit val bsonHandler: BSONHandler[BSONDocument, Phase2TestProfile] = Phase2TestProfile.bsonHandler

  override def getTestGroup(applicationId: String): Future[Option[Phase2TestProfile]] = {
    getTestGroup(applicationId, phaseName)
  }

  override def getTestProfileByToken(token: String): Future[Phase2TestProfile] = {
    getTestProfileByToken(token, phaseName)
  }

  override def getTestProfileByCubiksId(cubiksUserId: Int): Future[Phase2TestProfileWithAppId] = {
    val query = BSONDocument("testGroups.PHASE2.tests" -> BSONDocument(
      "$elemMatch" -> BSONDocument("cubiksUserId" -> cubiksUserId)
    ))
    val projection = BSONDocument("applicationId" -> 1, s"testGroups.$phaseName" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(doc) =>
        val applicationId = doc.getAs[String]("applicationId").get
        val bsonPhase2 = doc.getAs[BSONDocument]("testGroups").map(_.getAs[BSONDocument](phaseName).get)
        val phase2TestGroup = bsonPhase2.map(Phase2TestProfile.bsonHandler.read).getOrElse(cannotFindTestByCubiksId(cubiksUserId))
        Phase2TestProfileWithAppId(applicationId, phase2TestGroup)
      case _ => cannotFindTestByCubiksId(cubiksUserId)
    }
  }

  override def updateGroupExpiryTime(applicationId: String, expirationDate: DateTime): Future[Unit] = {
    updateGroupExpiryTime(applicationId, expirationDate, phaseName)
  }

  override def insertOrUpdateTestGroup(applicationId: String, phase2TestProfile: Phase2TestProfile) = {
    val query = BSONDocument("applicationId" -> applicationId)

    val applicationStatusBSON = BSONDocument("$set" -> BSONDocument(
      s"progress-status.$PHASE1_TESTS_INVITED" -> true,
      "applicationStatus" -> PHASE1_TESTS_INVITED.applicationStatus
    )) ++ BSONDocument("$set" -> BSONDocument(
      "testGroups" -> BSONDocument(phaseName -> phase2TestProfile)
    ))

    collection.update(query, applicationStatusBSON, upsert = false) map { status =>
      if (status.n != 1) {
        val msg = s"${status.n} rows affected when inserting or updating instead of 1! (App Id: $applicationId)"
        Logger.warn(msg)
        throw UnexpectedException(msg)
      }
      ()
    }
  }

  override def insertTestResult(appId: String, phase2Test: CubiksTest, testResult: TestResult): Future[Unit] = {
    val query = BSONDocument(
      "applicationId" -> appId,
      s"testGroups.$phaseName.tests" -> BSONDocument(
        "$elemMatch" -> BSONDocument("cubiksUserId" -> phase2Test.cubiksUserId)
      )
    )

    val update = BSONDocument("$set" -> BSONDocument(
      s"progress-status.$PHASE1_TESTS_RESULTS_RECEIVED" -> true // TODO: FSET-696 This shouldn't be updated here. As not all results are saved
    )) ++ BSONDocument("$set" -> BSONDocument(
      s"testGroups.$phaseName.tests.$$.testResult" -> TestResult.testResultBsonHandler.write(testResult)
    ))

    collection.update(query, update, upsert = false) map( _ => () )
  }

  override def nextExpiringApplication: Future[Option[ExpiringOnlineTest]] = {
    val progressStatusQuery = BSONDocument("$and" -> BSONArray(
      BSONDocument("progress-status.PHASE2_TESTS_COMPLETED" -> BSONDocument("$ne" -> true)),
      BSONDocument("progress-status.PHASE2_TESTS_EXPIRED" -> BSONDocument("$ne" -> true))
    ))

  nextExpiringApplication(progressStatusQuery, phaseName)
  }

  override def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]] = {
      val progressStatusQuery = BSONDocument("$and" -> BSONArray(
        BSONDocument(s"progress-status.$PHASE1_TESTS_COMPLETED" -> BSONDocument("$ne" -> true)),
        BSONDocument(s"progress-status.$PHASE1_TESTS_EXPIRED" -> BSONDocument("$ne" -> true)),
        BSONDocument(s"progress-status.${reminder.progressStatuses}" -> BSONDocument("$ne" -> true))
      ))

    nextTestForReminder(reminder, phaseName, progressStatusQuery)
  }

  override def nextTestGroupWithReportReady: Future[Option[Phase2TestProfileWithAppId]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatus.PHASE2_TESTS),
      BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_RESULTS_READY}" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}" ->
        BSONDocument("$ne" -> true)
      )
    ))

    selectRandom(query).map(_.map { doc =>
      val group = doc.getAs[BSONDocument]("testGroups").get.getAs[BSONDocument](phaseName).get
      Phase2TestProfileWithAppId(
        applicationId = doc.getAs[String]("applicationId").get,
        Phase2TestProfile.bsonHandler.read(group)
      )
    })
  }

  override def removeTestProfileProgresses(appId: String, progressStatuses: List[ProgressStatus]): Future[Unit] = {
    require(progressStatuses.nonEmpty)
    require(progressStatuses forall (_.applicationStatus == ApplicationStatus.PHASE2_TESTS), "Cannot remove non Phase 1 progress status")

    val query = BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> ApplicationStatus.PHASE2_TESTS
    )
    val progressesToRemoveQueryPartial = progressStatuses map (p => s"progress-status.$p" -> BSONString(""))

    val updateQuery = BSONDocument("$unset" -> BSONDocument(progressesToRemoveQueryPartial))

    collection.update(query, updateQuery, upsert = false) map ( _ => () )
  }

}