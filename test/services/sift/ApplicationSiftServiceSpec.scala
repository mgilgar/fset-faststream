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

package services.sift

import connectors.EmailClient
import factories.DateTimeFactoryMock
import model._
import model.command.ApplicationForSift
import model.persisted.{ ContactDetailsExamples, SchemeEvaluationResult }
import org.joda.time.LocalDate
import repositories.sift.ApplicationSiftRepository
import testkit.ScalaMockUnitWithAppSpec
import testkit.ScalaMockImplicits._
import reactivemongo.bson.BSONDocument
import repositories.SchemeRepository
import repositories.application.GeneralApplicationRepository
import repositories.BSONDateTimeHandler
import repositories.contactdetails.ContactDetailsRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class ApplicationSiftServiceSpec extends ScalaMockUnitWithAppSpec {

  trait TestFixture  {
    val appId = "applicationId"
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockSiftRepo = mock[ApplicationSiftRepository]
    val mockEmailClient = mock[EmailClient]
    val mockContactDetailsRepo = mock[ContactDetailsRepository]
    val mockSchemeRepo = new SchemeRepository {
      override lazy val schemes: Seq[Scheme] = Seq(
        Scheme("DigitalAndTechnology", "DaT", "Digital and Technology", civilServantEligible = false, None, Some(SiftRequirement.FORM),
          siftEvaluationRequired = true, fsbType = None, telephoneInterviewType = None, schemeGuide = None
        ),
        Scheme("GovernmentSocialResearchService", "GSR", "GovernmentSocialResearchService", civilServantEligible = false, None, Some(SiftRequirement.FORM),
          siftEvaluationRequired = true, fsbType = None, telephoneInterviewType = None, schemeGuide = None
        ),
        Scheme("Commercial", "GCS", "Commercial", civilServantEligible = false, None, Some(SiftRequirement.NUMERIC_TEST),
          siftEvaluationRequired = true, fsbType = None, telephoneInterviewType = None, schemeGuide = None
        )
      )

      override def siftableSchemeIds: Seq[SchemeId] = Seq(SchemeId("GovernmentSocialResearchService"), SchemeId("Commercial"))
    }

    val service = new ApplicationSiftService {
      def applicationSiftRepo: ApplicationSiftRepository = mockSiftRepo
      def applicationRepo: GeneralApplicationRepository = mockAppRepo
      def schemeRepo: SchemeRepository = mockSchemeRepo
      def contactDetailsRepo: ContactDetailsRepository = mockContactDetailsRepo
      def emailClient: EmailClient = mockEmailClient
      def dateTimeFactory = DateTimeFactoryMock
    }

  }

  trait SiftUpdateTest extends TestFixture {
    val progressStatusUpdateBson = BSONDocument(
      s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> true,
      s"progress-status-timestamp.${ProgressStatuses.SIFT_COMPLETED}" -> BSONDateTimeHandler.write(DateTimeFactoryMock.nowLocalTimeZone)
    )

    def currentSchemeUpdateBson(schemeResult: SchemeEvaluationResult*) = BSONDocument(
        "currentSchemeStatus" -> schemeResult.map { s =>
          BSONDocument("schemeId" -> s.schemeId.value, "result" -> s.result)
        }
      )

    val schemeSiftResult = SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Green.toString)
    val queryBson = BSONDocument("applicationId" -> appId)
    val updateBson = BSONDocument("test" -> "test")
    (mockSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Nil)
  }


  "An ApplicationSiftService.progressApplicationToSift" must {

    "progress all applications regardless of failures" in new TestFixture {
      val applicationsToProgressToSift = List(
        ApplicationForSift("appId1", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString))),
        ApplicationForSift("appId2", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Green.toString))),
        ApplicationForSift("appId3",ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
            List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)))
      )

      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects("appId1", ProgressStatuses.SIFT_READY).returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects("appId2", ProgressStatuses.SIFT_ENTERED)
        .returning(Future.failed(new Exception))
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects("appId3", ProgressStatuses.SIFT_READY).returningAsync

      whenReady(service.progressApplicationToSiftStage(applicationsToProgressToSift)) { results =>

        val failedApplications = Seq(applicationsToProgressToSift(1))
        val passedApplications = Seq(applicationsToProgressToSift.head, applicationsToProgressToSift(2))
        results mustBe SerialUpdateResult(failedApplications, passedApplications)
      }
    }

    "find relevant applications for scheme sifting" in new TestFixture {
      val candidates = Seq(Candidate("userId1", Some("appId1"), Some(""), Some(""), Some(""), Some(""), Some(LocalDate.now), Some(Address("")),
        Some("E1 7UA"), Some("UK"), Some(ApplicationRoute.Faststream), Some("")))

      (mockSiftRepo.findApplicationsReadyForSchemeSift _).expects(*).returningAsync(candidates)

      whenReady(service.findApplicationsReadyForSchemeSift(SchemeId("scheme1"))) { result =>
        result mustBe candidates
      }
    }

    "sift and update progress status for a candidate" in new SiftUpdateTest {
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(schemeSiftResult),
        progressStatusUpdateBson
      )

      (mockAppRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Green.toString)
      ))
      (mockSiftRepo.siftApplicationForScheme _).expects(appId, schemeSiftResult, expectedUpdateBson).returningAsync
      (mockAppRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.Faststream)


      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

    "sift and update progress status for an SdipFaststream candidate" in new SiftUpdateTest {
      override val schemeSiftResult = SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Red.toString)
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(schemeSiftResult :: SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString) :: Nil : _*),
        progressStatusUpdateBson
      )
      (mockAppRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString)
      ))
      (mockSiftRepo.siftApplicationForScheme _).expects(appId, schemeSiftResult, expectedUpdateBson).returningAsync
      (mockAppRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.SdipFaststream)

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

    "sift a candidate with remaining schemes to sift" in new SiftUpdateTest {
       val currentStatus = Seq(
        SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)
      )
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(currentStatus:_*),
        BSONDocument.empty
      )

      (mockAppRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.Faststream)
      (mockAppRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(currentStatus)
      (mockSiftRepo.siftApplicationForScheme _).expects("applicationId", schemeSiftResult, expectedUpdateBson).returningAsync

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }
  }

  "sendSiftEnteredNotification" must {
    "send email to the right candidate" in new TestFixture {
      val candidate = CandidateExamples.minCandidate("userId")
      val contactDetails = ContactDetailsExamples.ContactDetailsUK

      (mockAppRepo.find(_ : String)).expects(appId).returningAsync(Some(candidate))
      (mockContactDetailsRepo.find _ ).expects("userId").returningAsync(contactDetails)
      (mockEmailClient.notifyCandidateSiftEnteredAdditionalQuestions(_: String, _: String)(_: HeaderCarrier))
        .expects(contactDetails.email, candidate.name, *).returningAsync

      whenReady(service.sendSiftEnteredNotification(appId)(new HeaderCarrier())) { result => result mustBe unit }
    }
  }

}
