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

package repositories.application

import model.persisted.{ FsbTestGroup, SchemeEvaluationResult }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FsbTestGroupRepository {
  def save(applicationId: String, result: SchemeEvaluationResult): Future[Unit]
  def findByApplicationId(applicationId: String): Future[Option[FsbTestGroup]]
}

class FsbTestGroupMongoRepository(implicit mongo: () => DB) extends
  ReactiveRepository[FsbTestGroup, BSONObjectID](CollectionNames.APPLICATION, mongo, FsbTestGroup.format,
    ReactiveMongoFormats.objectIdFormats) with FsbTestGroupRepository with ReactiveRepositoryHelpers {

  private val APPLICATION_ID = "applicationId"
  private val FSB_TEST_GROUPS = "testGroups.FSB"

  override def save(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {
    val selector = BSONDocument("$and" -> BSONArray(
      BSONDocument(APPLICATION_ID -> applicationId),
      BSONDocument(s"$FSB_TEST_GROUPS.evaluation.result.schemeId" -> BSONDocument("$nin" -> BSONArray(result.schemeId.value)))))
    val modifier = BSONDocument("$addToSet" -> BSONDocument(s"$FSB_TEST_GROUPS.evaluation.result" -> result))
    val validator = singleUpsertValidator(applicationId, actionDesc = "saving fsb assessment result")
    collection.update(selector, modifier, upsert = true) map validator
  }

  def findByApplicationId(applicationId: String): Future[Option[FsbTestGroup]] = {
    val query = BSONDocument(APPLICATION_ID -> applicationId)
    val projection = BSONDocument(FSB_TEST_GROUPS -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) =>
        for {
          testGroups <- document.getAs[BSONDocument]("testGroups")
          fsb <- testGroups.getAs[FsbTestGroup]("FSB")
        } yield fsb
      case _ => None
    }
  }

}
