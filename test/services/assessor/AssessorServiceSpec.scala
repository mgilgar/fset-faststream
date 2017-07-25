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

package services.assessor

import org.mockito.ArgumentMatchers.{ eq => eqTo }
import org.mockito.Mockito._
import services.BaseServiceSpec
import services.assessoravailability.AssessorService
import org.mockito.ArgumentMatchers._

import scala.concurrent.duration._
import model.{ AllocationStatuses, Exceptions, SerialUpdateResult }
import model.Exceptions.AssessorNotFoundException
import model.exchange.{ AssessorAvailabilities, UpdateAllocationStatusRequest }
import model.persisted.{ AssessorAllocation, EventExamples, ReferenceData }
import model.persisted.eventschedules.{ Location, Venue }
import model.persisted.assessor.AssessorExamples._
import repositories.{ AllocationRepository, AssessorRepository }
import repositories.events.{ EventsRepository, LocationsWithVenuesRepository }

import scala.concurrent.{ Await, Future }
import scala.language.postfixOps

class AssessorServiceSpec extends BaseServiceSpec {

  "save assessor" must {

    "save NEW assessor when assessor is new" in new TestFixture {

      when(mockAssessorRepository.find(eqTo(AssessorUserId))).thenReturn(Future.successful(None))
      when(mockAssessorRepository.save(eqTo(AssessorNew))).thenReturn(Future.successful(()))
      val response = service.saveAssessor(AssessorUserId, model.exchange.Assessor.apply(AssessorNew)).futureValue
      response mustBe unit
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
      verify(mockAssessorRepository).save(eqTo(AssessorNew))
    }

    "update skills and do not update availability when assessor previously EXISTED" in new TestFixture {

      when(mockAssessorRepository.find(eqTo(AssessorUserId))).thenReturn(Future.successful(Some(AssessorExisting)))
      when(mockAssessorRepository.save(eqTo(AssessorMerged))).thenReturn(Future.successful(()))
      val response = service.saveAssessor(AssessorUserId, model.exchange.Assessor.apply(AssessorNew)).futureValue
      response mustBe unit
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
      verify(mockAssessorRepository).save(eqTo(AssessorMerged))
    }
  }

  "add availability" must {

    "throw assessor not found exception when assessor cannot be found" in new TestFixture {

      when(mockAssessorRepository.find(eqTo(AssessorUserId))).thenReturn(Future.successful(None))

      val exchangeAvailability = AssessorWithAvailability.availability.map(model.exchange.AssessorAvailability.apply)
      val availabilities = AssessorAvailabilities(UserId, None, exchangeAvailability)


      intercept[AssessorNotFoundException] {
        Await.result(service.saveAvailability(availabilities), 10 seconds)
      }
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
    }

    "merge availability to EXISTING assessor" in new TestFixture {

      when(mockAssessorRepository.find(eqTo(AssessorUserId))).thenReturn(Future.successful(Some(AssessorExisting)))
      when(mockAssessorRepository.save(eqTo(AssessorWithAvailabilityMerged))).thenReturn(Future.successful(()))
      when(mockLocationsWithVenuesRepo.location(any[String])).thenReturn(Future.successful(EventExamples.LocationLondon))

      val exchangeAvailability = AssessorWithAvailability.availability.map(model.exchange.AssessorAvailability.apply)

      val availabilties = AssessorAvailabilities(AssessorUserId, None, exchangeAvailability)
      val result = service.saveAvailability(availabilties).futureValue

      result mustBe unit

      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
      verify(mockAssessorRepository).save(eqTo(AssessorWithAvailabilityMerged))
    }
  }


  "find assessor" must {
    "return assessor details" in new TestFixture {
      when(mockAssessorRepository.find(AssessorUserId)).thenReturn(Future.successful(Some(AssessorExisting)))

      val response = service.findAssessor(AssessorUserId).futureValue

      response mustBe model.exchange.Assessor(AssessorExisting)
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
    }

    "throw exception when there is no assessor" in new TestFixture {
      when(mockAssessorRepository.find(AssessorUserId)).thenReturn(Future.successful(None))

      intercept[Exceptions.AssessorNotFoundException] {
        Await.result(service.findAssessor(AssessorUserId), 10 seconds)
      }
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
    }
  }

  "find assessor availability" must {

    "return assessor availability" in new TestFixture {
      when(mockAssessorRepository.find(AssessorUserId)).thenReturn(Future.successful(Some(AssessorWithAvailability)))

      val response = service.findAvailability(AssessorUserId).futureValue

      val expected = AssessorWithAvailability.availability.map { a => model.exchange.AssessorAvailability.apply(a)}

      response mustBe expected
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
    }

    "throw exception when there are is assessor" in new TestFixture {
      when(mockAssessorRepository.find(AssessorUserId)).thenReturn(Future.successful(None))

      intercept[Exceptions.AssessorNotFoundException] {
        Await.result(service.findAvailability(AssessorUserId), 10 seconds)
      }
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
    }
  }

  "updating an assessors allocation status" must {
    "return a successful update response" in new TestFixture {
      when(mockAllocationRepo.updateAllocationStatus("assessorId", "eventId", AllocationStatuses.CONFIRMED))
        .thenReturn(Future.successful(unit))

      val updates = UpdateAllocationStatusRequest("assessorId", "eventId", AllocationStatuses.CONFIRMED) :: Nil
      val result = service.updateAssessorAllocationStatuses(updates).futureValue
      result.failures mustBe Nil
      result.successes mustBe updates
    }

    "return a failed response" in new TestFixture {
      when(mockAllocationRepo.updateAllocationStatus("assessorId", "eventId", AllocationStatuses.CONFIRMED))
        .thenReturn(Future.failed(new Exception("something went wrong")))

      val updates = UpdateAllocationStatusRequest("assessorId", "eventId", AllocationStatuses.CONFIRMED) :: Nil
      val result = service.updateAssessorAllocationStatuses(updates).futureValue
      result.failures mustBe updates
      result.successes mustBe Nil
    }

    "return a partial update response" in new TestFixture {
       when(mockAllocationRepo.updateAllocationStatus("assessorId", "eventId", AllocationStatuses.CONFIRMED))
        .thenReturn(Future.successful(unit))
      when(mockAllocationRepo.updateAllocationStatus("assessorId", "eventId2", AllocationStatuses.CONFIRMED))
        .thenReturn(Future.failed(new Exception("something went wrong")))

      val updates = UpdateAllocationStatusRequest("assessorId", "eventId", AllocationStatuses.CONFIRMED) ::
        UpdateAllocationStatusRequest("assessorId", "eventId2", AllocationStatuses.CONFIRMED) :: Nil
      val result = service.updateAssessorAllocationStatuses(updates).futureValue
      result.failures mustBe updates.last :: Nil
      result.successes mustBe updates.head :: Nil
    }
  }

  trait TestFixture {
    val mockAssessorRepository = mock[AssessorRepository]
    val mockLocationsWithVenuesRepo = mock[LocationsWithVenuesRepository]
    val mockAllocationRepo = mock[AllocationRepository[model.persisted.AssessorAllocation]]
    val mockEventRepo = mock[EventsRepository]
    val virtualVenue = Venue("virtual", "virtual venue")
    val venues = ReferenceData(List(Venue("london fsac", "bush house"), virtualVenue), virtualVenue, virtualVenue)

    when(mockLocationsWithVenuesRepo.venues).thenReturn(Future.successful(venues))

    val service = new AssessorService {
      val assessorRepository: AssessorRepository = mockAssessorRepository
      val allocationRepo: AllocationRepository[AssessorAllocation] = mockAllocationRepo
      val eventsRepo: EventsRepository = mockEventRepo
      val locationsWithVenuesRepo: LocationsWithVenuesRepository = mockLocationsWithVenuesRepo
    }
  }
}
