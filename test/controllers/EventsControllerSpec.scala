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

import config.TestFixtureBase
import model.persisted.eventschedules.{ EventType, VenueType }
import org.mockito.Mockito._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.events.EventsRepository
import services.assessmentcentre.AssessmentCentreParsingService
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class EventsControllerSpec extends UnitWithAppSpec {

  "Upload assessment events" should {
    "return CREATED with valid input" in new TestFixture {
      when(mockAssessmentCentreParsingService.processCentres()).thenReturn(Future.successful(events))
      when(mockAssessmentEventsRepo.save(events)).thenReturn(Future.successful(()))

      val res = controller.saveAssessmentEvents()(FakeRequest())
      status(res) mustBe CREATED
    }

    "return UNPROCESSABLE_ENTITY when saving goes wrong" in new TestFixture {
      when(mockAssessmentCentreParsingService.processCentres()).thenReturn(Future.successful(events))
      when(mockAssessmentEventsRepo.save(events)).thenReturn(Future.failed(new Exception))

      val res = controller.saveAssessmentEvents()(FakeRequest())
      status(res) mustBe UNPROCESSABLE_ENTITY
    }

    "return UNPROCESSABLE_ENTITY when parsing goes wrong" in new TestFixture {
      when(mockAssessmentCentreParsingService.processCentres()).thenReturn(Future.failed(new Exception))
      when(mockAssessmentEventsRepo.save(events)).thenReturn(Future.successful(()))

      val res = controller.saveAssessmentEvents()(FakeRequest())
      status(res) mustBe UNPROCESSABLE_ENTITY
    }

    "return OK with all events" in new TestFixture {
      when(mockAssessmentEventsRepo.fetchEvents(Some(EventType.FSAC), Some(VenueType.LONDON_FSAC), None, None))
        .thenReturn(Future.successful(List()))
      val res = controller.fetchEvents("fsac", "london_fsac")(FakeRequest())
      status(res) mustBe OK
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockAssessmentCentreParsingService = mock[AssessmentCentreParsingService]
    val mockAssessmentEventsRepo = mock[EventsRepository]
    val events = List()
    val controller = new EventsController {
      override val assessmentEventsRepository: EventsRepository = mockAssessmentEventsRepo
      override val assessmentCenterParsingService: AssessmentCentreParsingService = mockAssessmentCentreParsingService
    }
  }
}
