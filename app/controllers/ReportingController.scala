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

package controllers

import connectors.{ AuthProviderClient, ExchangeObjects }
import model.EvaluationResults.Green
import model.Exceptions.PersonalDetailsNotFound
import model.{ SiftRequirement, UniqueIdentifier }
import model.persisted.ContactDetailsWithId
import model.persisted.eventschedules.Event
import model.report._
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent }
import repositories.application.{ ReportingMongoRepository, ReportingRepository }
import repositories.contactdetails.ContactDetailsMongoRepository
import repositories.csv.FSACIndicatorCSVRepository
import repositories.events.EventsRepository
import repositories.{ QuestionnaireRepository, _ }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.collection.breakOut
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ReportingController extends ReportingController {
  val reportingRepository: ReportingMongoRepository = repositories.reportingRepository
  val assessorRepository: AssessorRepository = repositories.assessorRepository
  val eventsRepository: EventsRepository = repositories.eventsRepository
  val assessorAllocationRepository: AssessorAllocationRepository = repositories.assessorAllocationRepository
  val contactDetailsRepository: ContactDetailsMongoRepository = repositories.faststreamContactDetailsRepository
  val questionnaireRepository: QuestionnaireMongoRepository = repositories.questionnaireRepository
  val assessmentScoresRepository: AssessmentScoresMongoRepository = repositories.reviewerAssessmentScoresRepository
  val mediaRepository: MediaMongoRepository = repositories.mediaRepository
  val fsacIndicatorCSVRepository: FSACIndicatorCSVRepository = repositories.fsacIndicatorCSVRepository
  val schemeRepo: SchemeRepository = SchemeYamlRepository
  val authProviderClient: AuthProviderClient = AuthProviderClient
  val candidateAllocationRepo = repositories.candidateAllocationRepository
}

trait ReportingController extends BaseController {

  val reportingRepository: ReportingRepository
  val assessorRepository: AssessorRepository
  val eventsRepository: EventsRepository
  val assessorAllocationRepository: AssessorAllocationRepository
  val contactDetailsRepository: contactdetails.ContactDetailsRepository
  val questionnaireRepository: QuestionnaireRepository
  val assessmentScoresRepository: AssessmentScoresRepository
  val mediaRepository: MediaRepository
  val fsacIndicatorCSVRepository: FSACIndicatorCSVRepository
  val schemeRepo: SchemeRepository
  val authProviderClient: AuthProviderClient
  val candidateAllocationRepo: CandidateAllocationRepository

  def internshipReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      applications <- reportingRepository.applicationsForInternshipReport(frameworkId)
      contactDetails <- contactDetailsRepository.findByUserIds(applications.map(_.userId)).map(cdList => contactDetailsToMap(cdList))
    } yield {
      Ok(Json.toJson(buildInternshipReportItems(applications, contactDetails)))
    }
  }

  private def contactDetailsToMap(contactDetailsList: List[ContactDetailsWithId]) = contactDetailsList.map(cd => cd.userId -> cd).toMap

  private def buildInternshipReportItems(applications: List[ApplicationForInternshipReport],
    contactDetailsMap: Map[String, ContactDetailsWithId]
  ): List[InternshipReportItem] = {
    applications.map { application =>
      val contactDetails = contactDetailsMap.getOrElse(application.userId,
        throw new IllegalStateException(s"No contact details found for user Id = ${application.userId}")
      )
      InternshipReportItem(application, contactDetails)
    }
  }

  def analyticalSchemesReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val applicationsFut = reportingRepository.applicationsForAnalyticalSchemesReport(frameworkId)
    val reportFut = for {
      applications <- applicationsFut
      contactDetails <- contactDetailsRepository.findByUserIds(applications.map(_.userId)).map(cdList => contactDetailsToMap(cdList))
    } yield {
      buildAnalyticalSchemesReportItems(applications, contactDetails)
    }
    reportFut.map { report =>
      Ok(Json.toJson(report))
    }
  }

  private def makeRow(values: Option[String]*) =
    values.map { s =>
      val ret = s.getOrElse(" ").replace("\r", " ").replace("\n", " ").replace("\"", "'")
      "\"" + ret + "\""
    }.mkString(",")

  def assessorAllocationReport: Action[AnyContent] = Action.async { implicit request =>

    import common.Joda._

    val sortedEventsFut = eventsRepository.findAll().map(_.sortBy(_.date))

    val reportRows = for {
      allAssessors <- assessorRepository.findAll()
      allAssessorsPersonalInfo <- authProviderClient.findByUserIds(allAssessors.map(_.userId))
        .map(
          _.map(x => x.userId -> x)(breakOut): Map[String, ExchangeObjects.Candidate]
        )
      sortedEvents <- sortedEventsFut
      assessorAllocations <- assessorAllocationRepository.findAll().map(_.groupBy(_.id))
    } yield for {
      theAssessor <- allAssessors
      theAssessorAuthProviderInfo = allAssessorsPersonalInfo(theAssessor.userId)
      theAssessorAllocations = assessorAllocations.getOrElse(theAssessor.userId, Nil)
    } yield {

      val roleByDate = sortedEvents.map { event =>
        theAssessorAllocations.find(_.eventId == event.id).map(allocation =>
          s"${allocation.allocatedAs.toString} (${allocation.status.toString})"
        ).orElse {
          val availabilities = theAssessor.availability.filter(_.date == event.date)
          if (availabilities.isEmpty) {
            None
          } else {
            Some(availabilities.map(_.location.name).mkString(", "))
          }
        }
      }

      val assessorInfo = List(Some(s"${theAssessorAuthProviderInfo.firstName} ${theAssessorAuthProviderInfo.lastName}"),
        Some(theAssessorAuthProviderInfo.roles.mkString(", ")), Some(theAssessor.skills.mkString(", ")),
        Some(theAssessor.sifterSchemes.map(_.toString).mkString(", ")),
        Some(theAssessorAuthProviderInfo.email), theAssessorAuthProviderInfo.phone,
        Some(if (theAssessor.civilServant) { "Internal" } else { "External" }))

      makeRow(assessorInfo ++ roleByDate: _*)
    }

    sortedEventsFut.flatMap { events =>
      val orderedDates = events.map(event => s"${event.date} (${event.eventType.toString})").mkString(",")
      val headers = List(
        s"Name,Role,Skills,Sift schemes,Email,Phone,Internal/External,$orderedDates"
      )

      reportRows.map { rows =>
        Ok(Json.toJson(headers ++ rows))
      }
    }
  }

  private def buildAnalyticalSchemesReportItems(applications: List[ApplicationForAnalyticalSchemesReport],
    contactDetailsMap: Map[String, ContactDetailsWithId]): List[AnalyticalSchemesReportItem] = {
    applications.map { application =>
      val contactDetails = contactDetailsMap.getOrElse(application.userId,
        throw new IllegalStateException(s"No contact details found for user Id = ${application.userId}")
      )
      AnalyticalSchemesReportItem(application, contactDetails)
    }
  }

  def adjustmentReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val reports =
      for {
        applications <- reportingRepository.adjustmentReport(frameworkId)
        allCandidates <- contactDetailsRepository.findAll
        candidates = allCandidates.groupBy(_.userId).mapValues(_.head)
      } yield {
        applications.map { application =>
          candidates
            .get(application.userId)
            .fold(application)(cd =>
              application.copy(email = Some(cd.email), telephone = cd.phone))
        }
      }

    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def candidateProgressReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val candidatesFut: Future[List[CandidateProgressReportItem]] = reportingRepository.candidateProgressReport(frameworkId)

    for {
      candidates <- candidatesFut
    } yield Ok(Json.toJson(candidates))
  }

  def preSubmittedCandidatesReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val reportItemsFut = reportingRepository.preSubmittedApplications(frameworkId).flatMap { applications =>
      authProviderClient.findByUserIds(applications.map(_.userId)).flatMap { authDetails =>
        Future.sequence(applications.map { application =>
          val user = authDetails.find(_.userId == application.userId)
            .getOrElse(throw new Exception(s"Unable to find auth details for user ${application.userId}"))
          personalDetailsRepository.find(application.applicationId).map { pd =>
            PreSubmittedReportItem(user, Some(pd.preferredName), application)
          }.recover { case _: PersonalDetailsNotFound => PreSubmittedReportItem(user, None, application)}
        })
      }
    }

    reportItemsFut.map(items => Ok(Json.toJson(items)))
  }

  def candidateDeferralReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      eventualCandidates <- reportingRepository.candidateDeferralReport(frameworkId)
      eventualContactDetails <- contactDetailsRepository.findAll
      contactDetailsByUserId = eventualContactDetails.groupBy(_.userId).mapValues(_.head)
    } yield {
      val data = eventualCandidates.map { candidate =>
        contactDetailsByUserId.get(candidate.userId).map { cd =>
          CandidateDeferralReportItem(
            candidateName = s"${candidate.firstName} ${candidate.lastName}",
            preferredName = candidate.preferredName,
            email = cd.email,
            address = cd.address,
            postCode = cd.postCode,
            telephone = cd.phone,
            programmes = candidate.partnerProgrammes
          )
        }
      }

      Ok(Json.toJson(data))
    }
  }

  def diversityReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val reports = for {
      applications <- reportingRepository.diversityReport(frameworkId)
      questionnaires <- questionnaireRepository.findAllForDiversityReport
      medias <- mediaRepository.findAll()
    } yield {
      applications.map { application =>
        DiversityReportItem(
          ApplicationForDiversityReportItem.create(application),
          questionnaires.get(application.applicationId),
          medias.get(application.userId).map { m => MediaReportItem(m.media) })
      }
    }
    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def timeToOfferReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val reports = for {
      applicationsForTimeToOffer <- reportingRepository.candidatesForTimeToOfferReport
      appsByUserId <- reportingRepository.diversityReport(frameworkId).map(_.groupBy(_.userId).mapValues(_.head))
      questionnairesByAppId <- questionnaireRepository.findAllForDiversityReport
      mediasByUserId <- mediaRepository.findAll()
      candidatesByUserId <- contactDetailsRepository.findAll.map(_.groupBy(_.userId).mapValues(_.head))
    } yield {
      applicationsForTimeToOffer.map { appTimeToOffer =>
        val userId = appTimeToOffer.userId
        val application = appsByUserId(userId)
        val appId = application.applicationId
        val contactDetails = candidatesByUserId.get(userId)
        val email = contactDetails.map(_.email)
        val diversityReportItem = DiversityReportItem(
          ApplicationForDiversityReportItem.create(application),
          questionnairesByAppId.get(appId),
          mediasByUserId.get(userId).map(m => MediaReportItem(m.media))
        )

        TimeToOfferItem(appTimeToOffer, email, diversityReportItem)
      }
    }
    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def onlineTestPassMarkReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
    val reports =
      for {
        applications <- reportingRepository.onlineTestPassMarkReport
        fsacResults <- assessmentScoresRepository.findAll
        questionnaires <- questionnaireRepository.findForOnlineTestPassMarkReport(applications.map(_.applicationId))
      } yield {
        for {
          a <- applications
          fr = fsacResults.find(_.applicationId == UniqueIdentifier(a.applicationId))
          q <- questionnaires.get(a.applicationId)
        } yield OnlineTestPassMarkReportItem(ApplicationForOnlineTestPassMarkReportItem.create(a, fr), q)
      }
    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def numericTestExtractReport(): Action[AnyContent] = Action.async { implicit request =>

    val numericTestSchemeIds = schemeRepo.schemes.collect {
      case scheme if scheme.siftEvaluationRequired && scheme.siftRequirement.contains(SiftRequirement.NUMERIC_TEST) => scheme.id
    }

    val reports =
      for {
        applications <- reportingRepository.numericTestExtractReport.map(_.filter { app =>
          val successfulSchemesSoFarIds = app.currentSchemeStatus.collect {
            case evalResult if evalResult.result == Green.toString => evalResult.schemeId
          }
          successfulSchemesSoFarIds.exists(numericTestSchemeIds.contains)
        })
        contactDetails <- contactDetailsRepository.findByUserIds(applications.map(_.userId))
          .map(
            _.map(x => x.userId -> x)(breakOut): Map[String, ContactDetailsWithId]
          )
        questionnaires <- questionnaireRepository.findForOnlineTestPassMarkReport(applications.map(_.applicationId))
      } yield for {
        a <- applications
        c <- contactDetails.get(a.userId)
        q <- questionnaires.get(a.applicationId)
      } yield NumericTestExtractReportItem(a, c, q)

      reports.map(list => Ok(Json.toJson(list)))
  }

  def candidateAcceptanceReport(): Action[AnyContent] = Action.async { implicit request =>

    val headers = Seq("Candidate email, allocation date, event type, event description, location, venue")
    candidateAllocationRepo.allAllocationUnconfirmed.flatMap { candidateAllocations =>
      for {
        events <- eventsRepository.getEventsById(candidateAllocations.map(_.eventId))
        candidates <- applicationRepository.find(candidateAllocations.map(_.id))
        contactDetails <- contactDetailsRepository.findByUserIds(candidates.map(_.userId))
      } yield {
        val eventMap: Map[String, Event] = events.map(e => e.id -> e)(breakOut)
        val cdMap: Map[String, ContactDetailsWithId] =
          candidates.map(c => c.applicationId.get -> contactDetails.find(_.userId == c.userId).get)(breakOut)

        val report = headers ++ candidateAllocations.map { allocation =>
          val e = eventMap(allocation.eventId)
          makeRow(List(Some(cdMap(allocation.id).email), Some(allocation.createdAt.toString), Some(e.eventType.toString),
            Some(e.description), Some(e.location.name), Some(e.venue.name)):_*
          )
        }
        Ok(Json.toJson(report))
      }
    }
  }


}
