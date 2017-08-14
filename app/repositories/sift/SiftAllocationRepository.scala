/*
 * Copyright 2017 HM Revenue & Customs
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

package repositories.sift

import java.time.LocalDateTime

import model.Exceptions.{ SchemeSpecificAnswerNotFound, SiftAllocationExists, SiftAnswersIncomplete, SiftAnswersSubmitted }
import model.SchemeId
import model.persisted.sift.SiftAnswersStatus.SiftAnswersStatus
import model.persisted.sift._
import org.joda.time.DateTime
import reactivemongo.api.DB
import reactivemongo.bson.Producer.nameValue2Producer
import reactivemongo.bson._
import repositories.{ BaseBSONReader, CollectionNames, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

trait SiftAllocationRepository {
  def addAllocation(siftAllocation: SiftAllocation): Future[SiftAllocation]
  def removeAllocation(siftAllocation: SiftAllocation): Future[Unit]
  def removeAllocationsOlderThan(dateTime: LocalDateTime): Future[Int]
  def updateExpiriesForUser(userId: String, newExpiry: LocalDateTime): Future[Int]
}

class SiftAllocationMongoRepository()(implicit mongo: () => DB)
  extends ReactiveRepository[SiftAllocation, BSONObjectID](CollectionNames.SIFT_ALLOCATIONS, mongo,
    SiftAllocation.siftAllocationFormat, ReactiveMongoFormats.objectIdFormats) with SiftAllocationRepository
    with ReactiveRepositoryHelpers with BaseBSONReader {

  def addAllocation(siftAllocation: SiftAllocation): Future[SiftAllocation] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> siftAllocation.applicationId),
      BSONDocument("schemeId" -> siftAllocation.schemeId))
    )

    // Atomic return existing or insert if not exists
    val allocation = collection.findAndUpdate(
      query,
      BSONDocument("$setOnInsert" -> siftAllocation),
      upsert = true,
      fetchNewObject = true
    ).one[SiftAllocation]

    allocation map { sa: SiftAllocation =>
      if (sa.userId != siftAllocation.userId) {
        Future.failed(SiftAllocationExists(sa, s"Allocation already exists for ${siftAllocation.applicationId} - ${siftAllocation.schemeId}"))
      } else {
        Future.successful(sa)
      }
    }
  }

  def removeAllocation(applicationId: String, schemeId: SchemeId, userId: String): Future[Unit] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument("schemeId" -> schemeId),
      BSONDocument("userId" -> userId))
    )

    collection.remove(query) map singleRemovalValidator(s"$applicationId - $schemeId - $userId", "Existing lock doesn't exist/match")
  }

  def removeAllocationsOlderThan(dateTime: LocalDateTime): Future[Int] = {
    val query = BSONDocument("created" -> BSONDocument("$lt" ->
      new DateTime(dateTime.getYear, dateTime.getMonth, dateTime.getDayOfMonth, dateTime.getHour, dateTime.getMinute, dateTime.getSecond))
    )

    collection.remove(query) map(_.n)
  }

  def updateExpiriesForUser(userId: String, newExpiry: LocalDateTime): Future[Int] = {
    val query = BSONDocument("userId" -> userId)

    collection.update(
      query,
      BSONDocument("$set" -> BSONDocument("expires" ->
        new DateTime(dateTime.getYear, dateTime.getMonth, dateTime.getDayOfMonth, dateTime.getHour, dateTime.getMinute, dateTime.getSecond))),
      upsert = false,
      multi = true
    ) map(_.n)
  }
}
