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

package services.assessmentscores

import factories.DateTimeFactory
import model.Exceptions.EventNotFoundException
import model.{ AllocationStatuses, UniqueIdentifier }
import model.assessmentscores.{ AssessmentScoresAllExercises, AssessmentScoresAllExercisesExamples, AssessmentScoresExerciseExamples }
import model.command.AssessmentScoresCommands.{ AssessmentExerciseType, AssessmentScoresFindResponse, RecordCandidateScores }
import model.command.PersonalDetailsExamples
import model.persisted.{ CandidateAllocation, EventExamples }
import org.joda.time.DateTimeZone
import org.mockito.Mockito.when
import repositories.{ AssessmentScoresRepository, CandidateAllocationMongoRepository }
import repositories.events.EventsRepository
import repositories.personaldetails.PersonalDetailsRepository
import services.BaseServiceSpec
import org.mockito.ArgumentMatchers.{ eq => eqTo }
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._

import scala.concurrent.Future

class AssessmentScoresServiceSpec extends BaseServiceSpec {

  "save" should {
    "save assessment scores with updated submitted date" in new SaveTestFixture {
      val updatedExample1 = AssessmentScoresAllExercisesExamples.Example1.copy(
        analysisExercise = AssessmentScoresAllExercisesExamples.Example1.analysisExercise.map(_.copy(submittedDate = Some(now))),
        groupExercise = AssessmentScoresAllExercisesExamples.Example1.groupExercise.map(_.copy(submittedDate = Some(now))),
        leadershipExercise = AssessmentScoresAllExercisesExamples.Example1.leadershipExercise.map(_.copy(submittedDate = Some(now)))
      )
      when(assessmentScoresRepositoryMock.save(eqTo(updatedExample1))).thenReturn(Future.successful(()))

      val result = service.save(AssessmentScoresAllExercisesExamples.Example1).futureValue
      verify(assessmentScoresRepositoryMock).save(eqTo(updatedExample1))
    }
  }

  "saveExercise" should {
    "update analysis exercise scores " +
      "when assessment scores exist and we specify we want to update analysis exercise scores" in new SaveExerciseTestFixture {
      val updatedExample1 = AssessmentScoresAllExercisesExamples.Example1.copy(
        analysisExercise = Some(AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))))
      when(assessmentScoresRepositoryMock.save(eqTo(updatedExample1))).thenReturn(Future.successful(()))

      service.saveExercise(appId, AssessmentExerciseType.analysisExercise, AssessmentScoresExerciseExamples.Example4).futureValue
      verify(assessmentScoresRepositoryMock).save(eqTo(updatedExample1))
    }

    "update group exercise scores " +
      "when assessment scores exist and we specify we want to update group exercise scores" in new SaveExerciseTestFixture {
      val updatedExample1 = AssessmentScoresAllExercisesExamples.Example1.copy(
        groupExercise = Some(AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))))
      when(assessmentScoresRepositoryMock.save(eqTo(updatedExample1))).thenReturn(Future.successful(()))

      service.saveExercise(appId, AssessmentExerciseType.groupExercise, AssessmentScoresExerciseExamples.Example4).futureValue
      verify(assessmentScoresRepositoryMock).save(eqTo(updatedExample1))
    }

    "update leadership exercise scores " +
      "when assessment scores exist and we specify we want to update leadership exercise scores" in new SaveExerciseTestFixture {
      val updatedExample1 = AssessmentScoresAllExercisesExamples.Example1.copy(
        leadershipExercise = Some(AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))))
      when(assessmentScoresRepositoryMock.save(eqTo(updatedExample1))).thenReturn(Future.successful(()))

      service.saveExercise(appId, AssessmentExerciseType.leadershipExercise, AssessmentScoresExerciseExamples.Example4).futureValue
      verify(assessmentScoresRepositoryMock).save(eqTo(updatedExample1))
    }

    "create assessment scores with analysis exercise scores " +
      "when assessment scores does not exist and we pass analysis exercise scores" in new SaveExerciseTestFixture {
      when(assessmentScoresRepositoryMock.find(eqTo(appId))).thenReturn(Future.successful(None))
      val expectedAssessmentScores = AssessmentScoresAllExercises(appId,
        Some(AssessmentScoresExerciseExamples.Example4.copy(submittedDate = Some(now))), None, None)
      when(assessmentScoresRepositoryMock.save(eqTo(expectedAssessmentScores))).thenReturn(Future.successful(()))

      service.saveExercise(appId, AssessmentExerciseType.analysisExercise,
        AssessmentScoresExerciseExamples.Example4).futureValue

      verify(assessmentScoresRepositoryMock).save(eqTo(expectedAssessmentScores))

    }
  }

  "findAssessmentScoresWithCandidateSummaryByApplicationId" should {
    "return Assessment Scores response with empty assessment scores if there is not any" in
      new FindAssessmentScoresWithCandidateSummaryTestFixture {

      when(assessmentScoresRepositoryMock.find(appId)).thenReturn(Future.successful(None))

      val result = service.findAssessmentScoresWithCandidateSummaryByApplicationId(appId).futureValue

      val expectedCandidate = RecordCandidateScores(
        appId,
        PersonalDetailsExamples.completed.firstName,
        PersonalDetailsExamples.completed.lastName,
        EventExamples.e1WithSessions.venue.description,
        today,
        UniqueIdentifier(EventExamples.e1WithSessions.sessions.head.id)
      )
      val expectedResult = AssessmentScoresFindResponse(expectedCandidate, None)
      result mustBe expectedResult
    }

    "return Assessment Scores response with assessment scores if there are assessment scores" in
      new FindAssessmentScoresWithCandidateSummaryTestFixture {
      when(assessmentScoresRepositoryMock.find(appId)).thenReturn(Future.successful(Some(AssessmentScoresAllExercisesExamples.Example1)))

      val result = service.findAssessmentScoresWithCandidateSummaryByApplicationId(appId).futureValue

      val expectedCandidate = RecordCandidateScores(
        appId,
        PersonalDetailsExamples.completed.firstName,
        PersonalDetailsExamples.completed.lastName,
        EventExamples.e1WithSessions.venue.description,
        today,
        UniqueIdentifier(EventExamples.e1WithSessions.sessions.head.id)
      )
      val expectedResult = AssessmentScoresFindResponse(expectedCandidate, Some(AssessmentScoresAllExercisesExamples.Example1))
      result mustBe expectedResult
    }
  }

  "findAssessmentScoresWithCandidateSummaryByEventId" should {
    "throw EventNotFoundException when event cannot be found" in
      new FindAssessmentScoresWithCandidateSummaryTestFixture {
        when(eventsRepositoryMock.getEvent(eventId)).thenReturn(Future.failed(new EventNotFoundException(s"No event found with id $eventId")))

        val ex = intercept[Exception] {
          service.findAssessmentScoresWithCandidateSummaryByEventId(UniqueIdentifier(eventId)).futureValue
        }
        ex.getCause mustBe (EventNotFoundException(s"No event found with id $eventId"))
      }

    "return List Assessment Scores find response with empty assessment scores if there is not any" in
      new FindAssessmentScoresWithCandidateSummaryTestFixture {
        when(assessmentScoresRepositoryMock.find(appId)).thenReturn(Future.successful(None))
        when(candidateAllocationRepositoryMock.allocationsForEvent(eventId)).thenReturn(Future.successful(candidateAllocations))

        val result = service.findAssessmentScoresWithCandidateSummaryByEventId(UniqueIdentifier(eventId)).futureValue

        val expectedCandidate = RecordCandidateScores(
          appId,
          PersonalDetailsExamples.completed.firstName,
          PersonalDetailsExamples.completed.lastName,
          EventExamples.e1WithSessions.venue.description,
          today,
          UniqueIdentifier(EventExamples.e1WithSessions.sessions.head.id)
        )
        val expectedResult = List(AssessmentScoresFindResponse(expectedCandidate, None))
        result mustBe expectedResult
      }

    "return Assessment Scores response with assessment scores if there are assessment scores" in
      new FindAssessmentScoresWithCandidateSummaryTestFixture {
        when(assessmentScoresRepositoryMock.find(appId)).thenReturn(Future.successful(Some(AssessmentScoresAllExercisesExamples.Example1)))
        when(candidateAllocationRepositoryMock.allocationsForEvent(eventId)).thenReturn(Future.successful(candidateAllocations))

        val result = service.findAssessmentScoresWithCandidateSummaryByEventId(UniqueIdentifier(eventId)).futureValue

        val expectedCandidate = RecordCandidateScores(
          appId,
          PersonalDetailsExamples.completed.firstName,
          PersonalDetailsExamples.completed.lastName,
          EventExamples.e1WithSessions.venue.description,
          today,
          UniqueIdentifier(EventExamples.e1WithSessions.sessions.head.id)
        )
        val expectedResult = List(AssessmentScoresFindResponse(expectedCandidate, Some(AssessmentScoresAllExercisesExamples.Example1)))
        result mustBe expectedResult
      }
  }

  trait BaseTestFixture {
    val assessmentScoresRepositoryMock = mock[AssessmentScoresRepository]
    val candidateAllocationRepositoryMock = mock[CandidateAllocationMongoRepository]
    val eventsRepositoryMock = mock[EventsRepository]
    val personalDetailsRepositoryMock = mock[PersonalDetailsRepository]

    val dataTimeFactoryMock = mock[DateTimeFactory]

    val service = new AssessmentScoresService {
      override val assessmentScoresRepository = assessmentScoresRepositoryMock
      override val candidateAllocationRepository = candidateAllocationRepositoryMock
      override val eventsRepository = eventsRepositoryMock
      override val personalDetailsRepository = personalDetailsRepositoryMock

      override val dateTimeFactory = dataTimeFactoryMock
    }

    val appId = AssessmentScoresAllExercisesExamples.Example1.applicationId
    val now = DateTimeFactory.nowLocalTimeZone.withZone(DateTimeZone.UTC)
    when(dataTimeFactoryMock.nowLocalTimeZone).thenReturn(now)
    val today = DateTimeFactory.nowLocalDate
    when(dataTimeFactoryMock.nowLocalDate).thenReturn(today)
  }

  trait SaveTestFixture extends BaseTestFixture

  trait SaveExerciseTestFixture extends BaseTestFixture {
    when(assessmentScoresRepositoryMock.find(eqTo(appId))).thenReturn(
      Future.successful(Some(AssessmentScoresAllExercisesExamples.Example1)))
  }

  trait FindAssessmentScoresWithCandidateSummaryTestFixture extends BaseTestFixture {
    val eventId = EventExamples.e1WithSessions.id
    val candidateAllocations = List(CandidateAllocation(appId.toString(), eventId, EventExamples.e1WithSessions.sessions.head.id,
      AllocationStatuses.CONFIRMED, "version1"))
    when(eventsRepositoryMock.getEvent(eventId)).thenReturn(Future.successful(EventExamples.e1WithSessions))
    when(candidateAllocationRepositoryMock.find(appId.toString())).thenReturn(Future.successful(candidateAllocations))
    when(personalDetailsRepositoryMock.find(appId.toString())).thenReturn(Future.successful(PersonalDetailsExamples.completed))
    when(eventsRepositoryMock.getEvent(eventId)).thenReturn(Future.successful(EventExamples.e1WithSessions))
  }
}