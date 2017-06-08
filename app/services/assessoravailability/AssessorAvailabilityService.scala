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

package services.assessoravailability

import connectors.{ AuthProviderClient, CSREmailClient }
import model.Exceptions.AssessorAvailabilityNotFoundException
import repositories._
import repositories.application.GeneralApplicationRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import language.postfixOps

object AssessorAvailabilityService extends AssessorAvailabilityService {
  val aRepository: GeneralApplicationRepository = applicationRepository
  val authProviderClient: AuthProviderClient = AuthProviderClient
  val aaRepository: AssessorAvailabilityMongoRepository = assessorAvailabilityRepository
  val assessmentCentreYamlRepository: AssessmentCentreRepository = AssessmentCentreYamlRepository
}

trait AssessorAvailabilityService {
  val aRepository: GeneralApplicationRepository
  val authProviderClient: AuthProviderClient
  val aaRepository: AssessorAvailabilityRepository
  val assessmentCentreYamlRepository: AssessmentCentreRepository

  lazy val regions: Future[Set[String]] = assessmentCentreYamlRepository.assessmentCentreCapacities.map(
    _.map(
      _.regionName.toLowerCase
    ).toSet
  )

  def save(userId: String, assessorAvailability: model.exchange.AssessorAvailability): Future[Unit] = {
    aaRepository.find(userId).flatMap {
      case Some(existing) =>
        val mergedAvailability = existing.availability ++ assessorAvailability.availability
        val assessorAvailabilityToPersist = model.persisted.AssessorAvailability(userId, mergedAvailability)
        aaRepository.save(assessorAvailabilityToPersist).map( _ => () )
      case _ =>
        val assessorAvailabilityToPersist = model.persisted.AssessorAvailability(userId, assessorAvailability.availability)
        aaRepository.save(assessorAvailabilityToPersist).map( _ => () )
    }
  }

  def find(userId: String): Future[model.exchange.AssessorAvailability] = {
    for {
      availabilityOpt <- aaRepository.find(userId)
    } yield {
      availabilityOpt.fold( throw AssessorAvailabilityNotFoundException(userId) ) {
        availability => model.exchange.AssessorAvailability(availability.userId, availability.availability)
      }
    }
  }

  def countSubmitted(): Future[Int] = {
    aaRepository.countSubmitted
  }

  def remindUnsubmitted()(implicit hc: HeaderCarrier): Future[Unit] = {
    // Select all unsubmitted in assessor or qac
    for {
      submittedUserIds <- aaRepository.submittedUserIds
      allQacAndAssessorUserIdsAndEmails <- authProviderClient.findAllByRoles(Seq(Assessor, QAC)).map(_.userId -> _.email) //Return candidate object
      unsubmittedUserIdsAndEmails = allQacAndAssessorUserIdsAndEmails.filterNot { case (k,v) => submittedUserIds.contains(k) }
      userIdEmailPair <- unsubmittedUserIdsAndEmails
    } yield {
      // Candidate object
      CSREmailClient.sendUnsubmittedAssessorReminderEmail(userIdEmailPair.email, "Mr Smith"), 20 seconds)
    }
    Future { () }
  }
}
