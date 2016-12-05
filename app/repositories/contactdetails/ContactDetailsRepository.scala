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

package repositories.contactdetails

import config.MicroserviceAppConfig
import model.Address
import model.Commands._
import model.Exceptions.{ ContactDetailsNotFound, ContactDetailsNotFoundForEmail }
import model.PersistedObjects.ContactDetailsWithId
import model.persisted.ContactDetails
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import repositories.ReactiveRepositoryHelpers
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ContactDetailsRepository {
  def update(userId: String, contactDetails: ContactDetails): Future[Unit]

  def find(userId: String): Future[ContactDetails]

  def findUserIdByEmail(email: String): Future[String]

  def findAll: Future[List[ContactDetailsWithId]]

  def findAllPostcodes(): Future[Map[String, String]]

  def archive(originalUserId: String, userIdToArchiveWith: String): Future[Unit]
}

class ContactDetailsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[ContactDetails, BSONObjectID]("contact-details", mongo, ContactDetails.contactDetailsFormat,
    ReactiveMongoFormats.objectIdFormats) with ContactDetailsRepository with ReactiveRepositoryHelpers {

  val ContactDetailsCollection = "contact-details"

  override def update(userId: String, contactDetails: ContactDetails): Future[Unit] = {
    val query = BSONDocument("userId" -> userId)
    val contactDetailsBson = BSONDocument("$set" -> BSONDocument(ContactDetailsCollection -> contactDetails))

    val validator = singleUpsertValidator(userId, actionDesc = s"updating contact details for $userId")

    collection.update(query, contactDetailsBson, upsert = true) map validator
  }

  override def find(userId: String): Future[ContactDetails] = {
    val query = BSONDocument("userId" -> userId)
    val projection = BSONDocument(ContactDetailsCollection -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument]("contact-details").isDefined =>
        document.getAs[ContactDetails]("contact-details").get
      case None => throw ContactDetailsNotFound(userId)
    }
  }

  override def findUserIdByEmail(email: String): Future[String] = {
    val query = BSONDocument("contact-details.email" -> email)
    val projection = BSONDocument("userId" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(d) if d.getAs[String]("userId").isDefined =>
        d.getAs[String]("userId").get
      case None => throw ContactDetailsNotFoundForEmail()
    }
  }

  override def findAll: Future[List[ContactDetailsWithId]] = {
    val query = BSONDocument()

    collection.find(query).cursor[BSONDocument]().collect[List](MicroserviceAppConfig.maxNumberOfDocuments).map(_.map { doc =>
      val id = doc.getAs[String]("userId").get
      val root = doc.getAs[BSONDocument]("contact-details").get
      val address = root.getAs[Address]("address").get
      val postCode = root.getAs[PostCode]("postCode")
      val phone = root.getAs[PhoneNumber]("phone")
      val email = root.getAs[String]("email").get

      ContactDetailsWithId(id, address, postCode, email, phone)
    })
  }

  def findAllPostcodes(): Future[Map[String, String]] = {
    val query = BSONDocument("contact-details.postCode" -> BSONDocument("$exists" -> true))
    val projection = BSONDocument("userId" -> 1, "contact-details.postCode" -> 1)
    implicit val tupleReads: Reads[(String, String)] = (
      (JsPath \ "userId").read[String] and
        (JsPath \ "contact-details" \ "postCode").read[String]
      )((_, _))
    val result = collection.find(query, projection).cursor[(String, String)](ReadPreference.nearest).collect[List]()
    result.map(_.toMap)
  }

  override def archive(originalUserId: String, userIdToArchiveWith: String): Future[Unit] = {
    val query = BSONDocument("userId" -> originalUserId)

    val updateWithArchiveUserId = BSONDocument("$set" -> BSONDocument(
      "originalUserId" -> originalUserId,
      "userId" -> userIdToArchiveWith
    ))

    val validator = singleUpdateValidator(originalUserId, actionDesc = "archiving contact details")
    collection.update(query, updateWithArchiveUserId) map validator
  }
}
