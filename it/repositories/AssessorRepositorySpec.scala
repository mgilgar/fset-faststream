package repositories

import factories.UUIDFactory
import model.{ SchemeId, UniqueIdentifier }
import model.persisted.EventExamples
import model.persisted.eventschedules.{ Location, SkillType }
import model.persisted.assessor.{ Assessor, AssessorAvailability, AssessorStatus }
import org.joda.time.LocalDate
import testkit.MongoRepositorySpec

class AssessorRepositorySpec extends MongoRepositorySpec {

  override val collectionName = CollectionNames.ASSESSOR

  def repository = new AssessorMongoRepository()

  private val userId = UniqueIdentifier.randomUniqueIdentifier.toString
  private val AssessorWithAvailabilities = Assessor(userId, None,
    List("assessor", "qac"), List(SchemeId("Sdip")), true,
    Set(AssessorAvailability(EventExamples.LocationLondon, new LocalDate(2017, 9, 11)),
      AssessorAvailability(EventExamples.LocationNewcastle, new LocalDate(2017, 9, 12))),
    AssessorStatus.AVAILABILITIES_SUBMITTED
  )


  "Assessor repository" should {
    "create indexes for the repository" in {
      val repo = repositories.assessorRepository

      val indexes = indexesWithFields(repo)
      indexes must contain(List("_id"))
      indexes must contain(List("userId"))
      indexes.size mustBe 2
    }

    "save and find the assessor" in {
      repository.save(AssessorWithAvailabilities).futureValue

      val result = repository.find(userId).futureValue
      result.get mustBe AssessorWithAvailabilities
    }

    "save and find all assessors" in {
      val secondAssessor = AssessorWithAvailabilities.copy(userId = "456")
      List(
        AssessorWithAvailabilities,
        secondAssessor
      ).foreach { assessor =>
        repository.save(assessor).futureValue
      }

      val result = repository.findAll().futureValue

      result must contain(AssessorWithAvailabilities)
      result must contain(secondAssessor)
    }

    "save assessor and add availabilities" in {
      repository.save(AssessorWithAvailabilities).futureValue

      val result = repository.find(userId).futureValue
      result.get mustBe AssessorWithAvailabilities

      val updated = AssessorWithAvailabilities.copy(
        availability = Set(
          AssessorAvailability(EventExamples.LocationLondon, new LocalDate(2017, 9, 11)),
          AssessorAvailability(EventExamples.LocationLondon, new LocalDate(2017, 10, 11)),
          AssessorAvailability(EventExamples.LocationNewcastle, new LocalDate(2017, 9, 12)))
      )
      repository.save(updated).futureValue

      val updatedResult = repository.find(userId).futureValue
      updatedResult.get mustBe updated
    }

    "count submitted availabilities" in {
      val availability = AssessorWithAvailabilities
      val availability2 = availability.copy(userId = "user2")

      repository.save(availability).futureValue
      repository.save(availability2).futureValue

      val result = repository.countSubmittedAvailability.futureValue

      result mustBe 2
    }

    "find assessors without availabilities given date and location" in {
      val london = Location("London")
      val newcastle = Location("Newcastle")
      val skills = List(SkillType.EXERCISE_MARKER)

      val availabilities = Set(
        AssessorAvailability(london, new LocalDate(2017, 8, 10)),
        AssessorAvailability(london, new LocalDate(2017, 8, 11)),
        AssessorAvailability(newcastle, new LocalDate(2017, 9, 10)),
        AssessorAvailability(newcastle, new LocalDate(2017, 10, 11))
      )

      def assessor = Assessor(UUIDFactory.generateUUID(), None, skills.map(_.toString), Nil, civilServant = true,
        Set.empty, AssessorStatus.CREATED)

      val assessorsWithAvailabilities = Seq(
        assessor.copy(skills = List(SkillType.ASSESSOR.toString)),
        assessor.copy(skills = List(SkillType.ASSESSOR.toString, SkillType.CHAIR.toString)),
        assessor.copy(status = AssessorStatus.AVAILABILITIES_SUBMITTED, availability = availabilities),
        assessor.copy(skills = Nil),
        assessor.copy(skills = List(SkillType.DEPARTMENTAL_ASSESSOR.toString, SkillType.EXERCISE_MARKER.toString))
      )

      assessorsWithAvailabilities.foreach { assessor =>
        repository.save(assessor).futureValue mustBe unit
      }

      val eventDate = new LocalDate(2017, 8, 10)
      val eventSkills = List(SkillType.ASSESSOR, SkillType.QUALITY_ASSURANCE_COORDINATOR)
      val result = repository.findUnavailableAssessors(eventSkills, london, eventDate).futureValue
      result.size mustBe 2
    }

    "save and remove assessor" in {
      repository.save(AssessorWithAvailabilities).futureValue

      repository.remove(UniqueIdentifier(userId)).futureValue

      val result = repository.find(userId.toString).futureValue
      result mustBe None
    }
  }
}
