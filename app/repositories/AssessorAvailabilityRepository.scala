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

package repositories

import model.persisted.AssessorAvailability
import play.api.libs.json.Json
import play.api.libs.json.Json.JsValueWrapper
import reactivemongo.api.DB
import reactivemongo.bson._
import services.assessoravailability.AssessorAvailabilityService
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssessorAvailabilityRepository {

  def find(userId: String): Future[Option[AssessorAvailability]]
  def save(settings: AssessorAvailability): Future[Unit]

  def countSubmitted: Future[Int]
}

class AssessorAvailabilityMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[AssessorAvailability, BSONObjectID](CollectionNames.ASSESSOR_AVAILABILITY, mongo,
    AssessorAvailability.persistedAssessorAvailabilityFormat,
    ReactiveMongoFormats.objectIdFormats) with AssessorAvailabilityRepository {

  override def find(userId: String): Future[Option[AssessorAvailability]] = {
    val query = BSONDocument(
      "userId" -> userId
    )

    collection.find(query).one[BSONDocument].map { docOpt =>
      docOpt.map { doc =>
        assessorAvailabilityHandler.read(doc)
      }
    }
  }

  override def save(assessorAvailability: AssessorAvailability): Future[Unit] = {
    val query = BSONDocument("userId" -> assessorAvailability.userId)
    val saveBson: BSONDocument = BSONDocument("$set" -> assessorAvailability)
    val insertIfNoRecordFound = true

    collection.update(query, saveBson, upsert = insertIfNoRecordFound).map( _ => () )
  }

  override def countSubmitted: Future[Int] = {
    val fields: List[(String, JsValueWrapper)] = AssessorAvailabilityService.regions.map { region =>
      s"availability.$region" -> Json.toJsFieldJsValueWrapper(Json.obj("$exists" -> true))
    }
    val query = Json.obj(fields:_*)

    collection.count(Some(query))
  }
}
