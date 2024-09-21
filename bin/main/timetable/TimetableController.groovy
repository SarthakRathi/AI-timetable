// File: grails-app/controllers/timetable/TimetableController.groovy
package timetable

import grails.converters.JSON
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class TimetableController {
    ExcelService excelService

    // Predefined lists
    private static final List<String> SUBJECTS = ['Math', 'Science', 'History', 'Art', 'Physical Education']
    private static final List<String> CLASSES = ['FY', 'SY', 'TY']
    private static final List<String> ROOMS = ['101', '102', '103', '104', '105']
    private static final List<String> TEACHERS = ['Rohan', 'Ravi', 'Ashwin', 'Karan', 'Priya']
    private static final List<String> WEEK_DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday']
    private static final List<String> TIME_SLOTS = ['09:00', '10:00', '11:00', '12:00']

    def index() {
        def selectedClass = params.selectedClass ?: CLASSES[0]
        def currentStep = params.currentStep ? params.int('currentStep') : 1

        // Initialize session.timetable if it doesn't exist
        if (!session.timetable) {
            session.timetable = [:]
        }

        // Ensure all days and slots are initialized for the selected class
        if (!session.timetable[selectedClass]) {
            session.timetable[selectedClass] = [:]
        }

        WEEK_DAYS.each { day ->
            if (!session.timetable[selectedClass][day]) {
                session.timetable[selectedClass][day] = [:]
            }
            TIME_SLOTS.each { time ->
                if (!session.timetable[selectedClass][day][time] == null) {
                    session.timetable[selectedClass][day][time] = []
                }
            }
        }

        generateLectureCards(selectedClass)
        def filteredSubjectDetails = filterSubjectDetailsByClass(selectedClass)
        def filteredTimetable = filterTimetableByClass(selectedClass)

        [
            subjectDetails: session.subjectDetails ?: [:],
            timetable: filteredTimetable,
            subjects: SUBJECTS,
            classes: CLASSES,
            teachers: TEACHERS,
            weekDays: WEEK_DAYS,
            timeSlots: TIME_SLOTS,
            selectedClass: selectedClass,
            lectureCards: session.lectureCards?[selectedClass] ?: [],
            currentStep: currentStep
        ]
    }

    def addSubject() {
        if (!session.subjectDetails) {
            session.subjectDetails = [:]
        }
        def key = "${params.subject}_${params.class}_${params.type}"
        int lecturesPerWeek = params.int('lecturesPerWeek')
        
        session.subjectDetails[key] = [
            subject: params.subject,
            teacher: params.teacher,
            class: params.class,
            lecturesPerWeek: lecturesPerWeek,
            type: params.type
        ]
        
        generateLectureCards(params.class)
        
        redirect(action: "index", params: [selectedClass: params.class])
    }

    private void generateLectureCards(String selectedClass) {
        if (!session.lectureCards) {
            session.lectureCards = [:]
        }
        session.lectureCards[selectedClass] = []

        if (!session.timetable) {
            session.timetable = [:]
        }
        if (!session.timetable[selectedClass]) {
            session.timetable[selectedClass] = [:]
        }

        def existingTimetable = session.timetable[selectedClass]
        
        filterSubjectDetailsByClass(selectedClass).each { key, details ->
            int assignedLectures = 0
            existingTimetable.each { day, slots ->
                slots.each { time, slotList ->
                    assignedLectures += slotList.count { it.subject == details.subject }
                }
            }
            def remainingLectures = details.lecturesPerWeek - assignedLectures
            
            if (remainingLectures > 0) {
                session.lectureCards[selectedClass] << [
                    id: "${details.subject}_${details.class}".toString(),
                    subject: details.subject,
                    teacher: details.teacher,
                    count: remainingLectures,
                    type: details.type // Ensure you're tracking the type if needed
                ]
            }
        }
    }


    
    def resetTimetable() {
        def selectedClass = params.selectedClass
        session.timetable?.remove(selectedClass)
        session.lectureCards?.remove(selectedClass)
        generateLectureCards(selectedClass)
        redirect(action: "index", params: [selectedClass: selectedClass])
    }

    def assignLecture() {
        log.info("Assigning lecture: ${request.JSON}")
        try {
            def data = request.JSON
            def selectedClass = data.selectedClass
            def lectureId = data.lectureId
            def day = data.day
            def time = data.time

            if (!selectedClass || !lectureId || !day || !time) {
                log.warn("Missing required data for lecture assignment")
                render([success: false, message: "Missing required data for lecture assignment"] as JSON)
                return
            }

            session.timetable[selectedClass] = session.timetable[selectedClass] ?: [:]
            session.timetable[selectedClass][day] = session.timetable[selectedClass][day] ?: [:]
            session.timetable[selectedClass][day][time] = session.timetable[selectedClass][day][time] ?: []

            def currentSlot = session.timetable[selectedClass][day][time]
            def lecture = session.lectureCards[selectedClass].find { it.id == lectureId }

            if (!lecture) {
                render([success: false, message: "Lecture not found"] as JSON)
                return
            }

            // Check if the teacher is available
            if (!isTeacherAvailable(selectedClass, day, time, lecture.teacher)) {
                render([success: false, message: "Teacher is not available at this time"] as JSON)
                return
            }

            if (canAddLectureToSlot(currentSlot, lecture)) {
                def newLecture = [
                    subject: lecture.subject,
                    teacher: lecture.teacher,
                    room: lecture.room ?: assignRoom(selectedClass, day, time),
                    type: lecture.type
                ]
                currentSlot.add(newLecture)

                // Decrement available lecture card count
                lecture.count--
                render([success: true, message: "Lecture assigned successfully", lecture: newLecture] as JSON)
            } else {
                String errorMessage = lecture.type == "Lecture" ?
                    "Cannot add lecture to a slot that already contains lectures or labs" :
                    "Cannot add lab to a slot that contains a lecture or already has 3 labs"
                render([success: false, message: errorMessage] as JSON)
            }
        } catch (Exception e) {
            log.error("Error during lecture assignment", e)
            render([success: false, message: e.message] as JSON)
        }
    }


    def generateTimetable() {
        def selectedClass = params.selectedClass
        def lectureCards = session.lectureCards[selectedClass] ?: []
        def timetable = session.timetable[selectedClass] ?: [:]

        // Initialize timetable structure if it doesn't exist
        WEEK_DAYS.each { day ->
            if (!timetable[day]) timetable[day] = [:]
            TIME_SLOTS.each { time ->
                if (!timetable[day][time]) timetable[day][time] = []
            }
        }

        // Separate lecture cards into lectures and labs
        def lectures = lectureCards.findAll { it.type == "Lecture" }
        def labs = lectureCards.findAll { it.type == "Lab" }

        // Assign lectures first
        assignSessions(lectures, timetable, selectedClass)

        // Then assign labs
        assignSessions(labs, timetable, selectedClass)

        session.timetable[selectedClass] = timetable
        session.lectureCards[selectedClass] = lectureCards

        render([success: true, timetable: timetable, lectureCards: lectureCards] as JSON)
    }

    private void assignSessions(List cards, Map timetable, String selectedClass) {
        cards.each { card ->
            def remainingLectures = card.count
            while (remainingLectures > 0) {
                def slot = findAvailableSlot(timetable, card, selectedClass)
                if (slot && isTeacherAvailable(selectedClass, slot.day, slot.time, card.teacher)) {
                    def lecture = [
                        subject: card.subject,
                        teacher: card.teacher,
                        class: selectedClass,
                        room: assignRoom(selectedClass, slot.day, slot.time),
                        type: card.type
                    ]

                    timetable[slot.day][slot.time] << lecture
                    remainingLectures--
                    card.count--
                } else {
                    // If no slot is available or teacher is not available, try next card
                    break
                }
            }
        }
    }

    private Map findExistingSlotWithSpace(Map timetable, Map card) {
        for (day in WEEK_DAYS) {
            for (time in TIME_SLOTS) {
                if (canAddLectureToSlot(timetable[day][time], card)) {
                    return [day: day, time: time]
                }
            }
        }
        return null
    }

    private boolean canAddLectureToSlot(List currentSlot, Map newLecture) {
        if (currentSlot.isEmpty()) {
            return true
        }

        if (newLecture.type == "Lecture") {
            return currentSlot.isEmpty()
        } else if (newLecture.type == "Lab") {
            return currentSlot.every { it.type == "Lab" } && currentSlot.size() < 3
        }

        return false
    }

    private Map findAvailableSlot(Map timetable, Map card, String selectedClass) {
        def availableSlots = []
        WEEK_DAYS.each { day ->
            TIME_SLOTS.each { time ->
                if (canAddLectureToSlot(timetable[day][time], card) && 
                    isTeacherAvailable(selectedClass, day, time, card.teacher)) {
                    availableSlots << [day: day, time: time]
                }
            }
        }
        return availableSlots ? availableSlots[new Random().nextInt(availableSlots.size())] : null
    }

    private boolean isTeacherAvailable(String selectedClass, String day, String time, String teacher) {
        return !session.timetable.any { classGroup, classTimetable ->
            classTimetable[day]?[time]?.any { session ->
                session.teacher == teacher
            }
        }
    }

    private String assignRoom(String selectedClass, String day, String time) {
        def availableRooms = ROOMS - session.timetable.values().collect { classTimetable ->
            classTimetable[day]?[time]?.collect { it.room }
        }.flatten().findAll()
        return availableRooms ? availableRooms[new Random().nextInt(availableRooms.size())] : "TBD"
    }
    private Map filterSubjectDetailsByClass(String classGroup) {
        return (session.subjectDetails ?: [:]).findAll { key, value -> value.class == classGroup } ?: [:]
    }

    private Map filterTimetableByClass(String classGroup) {
        session.timetable?[classGroup] ?: [:]
    }

    def downloadTimetable() {
        if (!session.timetable || !session.timetable[selectedClass]) {
        flash.message = "No timetable data available. Please create a timetable first."
        redirect(action: 'index')
        return
    }

        try {
            def selectedClass = params.selectedClass ?: CLASSES[0]
            def timetableData = session.timetable[selectedClass]
            
            log.info("Timetable data for $selectedClass: $timetableData")

            XSSFWorkbook workbook = excelService.generateTimetableExcel(timetableData, WEEK_DAYS, TIME_SLOTS)
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            response.setHeader("Content-Disposition", "attachment; filename=timetable.xlsx")
            workbook.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            workbook.close()
        } catch (Exception e) {
            log.error("Error generating Excel file: ${e.message}", e)
            flash.message = "Error generating Excel file: ${e.message}"
            redirect(action: 'index')
        }
    }

    def deleteSubject() {
    def key = params.key
    if (session.subjectDetails?.containsKey(key)) {
        session.subjectDetails.remove(key)
        render([success: true, message: "Subject deleted successfully"] as JSON)
    } else {
        render([success: false, message: "Subject not found"] as JSON)
    }
}

    private int calculateRemainingLectures(String classGroup) {
        int totalLectures = WEEK_DAYS.size() * TIME_SLOTS.size()
        int usedLectures = session.subjectDetails?.findAll { it.value.class == classGroup }
                                                  ?.collect { it.value.lecturesPerWeek }
                                                  ?.sum() ?: 0
        return totalLectures - usedLectures
    }

    def getRemainingLectures() {
        def classGroup = params.classGroup
        int remaining = calculateRemainingLectures(classGroup)
        render([remaining: remaining] as JSON)
    }
}