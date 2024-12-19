// File: grails-app/controllers/timetable/TimetableController.groovy
package timetable

import grails.converters.JSON
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.*
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.ss.util.CellRangeAddressList
import java.text.SimpleDateFormat


class TimetableController {

    // Predefined lists
    private static final List<String> SUBJECTS = ['Data Structures & Algorithms', 'Database Management System', 'Discrete Mathematics', 'Probability & Statistics', 'Design Thinking', 'Universal Human Value', 'Data Ethics', 'Artificial Intelligence', 'Deep Learning', 'Big Data Analytics', 'Data Communication & Networking', 'Research Methodology And IPR', 'Project-I', 'Professional Elective']
    private static final List<String> CLASSES = ['SY A', 'SY B', 'TY A']
    private static final List<String> CLASSROOMS = ['101', '102', '103', '104', '105']
    private static final List<String> LABROOMS = ['Lab1', 'Lab2', 'Lab3']
    private static final List<String> TUTORIALROOMS = ['Tutorial1', 'Tutorial2', 'Tutorial3']
    private static final List<String> TEACHERS = ['Nilesh Sable', 'Chandrakant Banchhor', 'Anuradha Yenkikar', 'Pradnya Mehta', 'Amol Patil', 'Pranjal Pandit', 'Vidya Gaikwad']
    private static final List<String> WEEK_DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
    private static final List<String> TIME_SLOTS = ['8:00', '09:00', '10:00', '11:00', '12:00', '1:00', '2:00', '3:00', '4:00', '5:00']
    
    
    def index() {
        def selectedClass = params.selectedClass ?: CLASSES[0]
        def currentStep = params.currentStep ? params.int('currentStep') : (session.currentStep ?: 1)
        session.currentStep = currentStep

        // Initialize session.timetable if it doesn't exist
        if (!session.timetable) {
            session.timetable = [:]
        }

        if (!session.teacherConstraints) {
            session.teacherConstraints = [:]
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
                if (session.timetable[selectedClass][day][time] == null) {
                    session.timetable[selectedClass][day][time] = []
                }
            }
        }

        if (!session.subjectDetails) {
            session.subjectDetails = [:]
        }

        generateLectureCards(selectedClass)
        def filteredSubjectDetails = filterSubjectDetailsByClass(selectedClass)
        def filteredTimetable = filterTimetableByClass(selectedClass)

        [
            subjectDetails: session.subjectDetails ?: [:],
            timetable: filteredTimetable,
            subjects: SUBJECTS,
            classes: CLASSES.collect { it.encodeAsJSON() },
            teachers: TEACHERS.collect { it.encodeAsJSON() },
            weekDays: WEEK_DAYS,
            timeSlots: TIME_SLOTS,
            selectedClass: selectedClass,
            lectureCards: session.lectureCards?[selectedClass] ?: [],
            currentStep: currentStep,
            labs: LABROOMS.collect { it.encodeAsJSON() },
            classrooms: CLASSROOMS.collect { it.encodeAsJSON() },
            tutorialRooms: TUTORIALROOMS.collect { it.encodeAsJSON() },
            allRooms: (CLASSROOMS + LABROOMS + TUTORIALROOMS).collect { it.encodeAsJSON() },
            allDays: WEEK_DAYS
        ]
    }

    def deleteMultipleSubjects() {
        def data = request.JSON
        def keys = data.keys

        try {
            keys.each { key ->
                if (session.subjectDetails?.containsKey(key)) {
                    session.subjectDetails.remove(key)
                }
            }
            
            resetTimetable()
            
            render([success: true, message: "Selected subjects deleted successfully"] as JSON)
        } catch (Exception e) {
            log.error("Error deleting multiple subjects", e)
            render([success: false, message: "Error deleting subjects: ${e.message}"] as JSON)
        }
    }

    private void generateLectureCards(String selectedClass) {
        if (!session.lectureCards) {
            session.lectureCards = [:]
        }
        if (!session.originalLectureCards) {
            session.originalLectureCards = [:]
        }
        session.lectureCards[selectedClass] = []

        if (!session.timetable) {
            session.timetable = [:]
        }
        if (!session.timetable[selectedClass]) {
            session.timetable[selectedClass] = [:]
        }

        def existingTimetable = session.timetable[selectedClass]
        def processedLabSlots = []
        
        filterSubjectDetailsByClass(selectedClass).each { key, details ->
            int assignedLectures = 0
            
            existingTimetable.each { day, slots ->
                slots.each { time, slotList ->
                    slotList.findAll { session ->
                        session.subject == details.subject && session.batch == details.batch
                    }.each { session ->
                        if (session.type == 'Lab') {
                            def labIdentifier = "${day}_${time}_${session.subject}_${session.batch}"
                            if (!processedLabSlots.contains(labIdentifier)) {
                                assignedLectures++
                                processedLabSlots << labIdentifier
                                def nextTimeIndex = TIME_SLOTS.indexOf(time) + 1
                                if (nextTimeIndex < TIME_SLOTS.size()) {
                                    def nextTime = TIME_SLOTS[nextTimeIndex]
                                    processedLabSlots << "${day}_${nextTime}_${session.subject}_${session.batch}"
                                }
                            }
                        } else {
                            assignedLectures++
                        }
                    }
                }
            }
            
            def totalLectures = details.lecturesPerWeek
            def remainingLectures = Math.max(0, totalLectures - assignedLectures)  // Ensure we don't go negative
            
            def cardBase = [
                subject: details.subject,
                teacher: details.teacher,
                totalLectures: totalLectures,
                count: remainingLectures,
                type: details.type,
                roomAllocation: details.roomAllocation,
                manualRoom: details.manualRoom
            ]
            
            if (details.type == "Lecture") {
                def card = cardBase + [
                    id: "${details.subject}_${details.class}_${details.type}".toString()
                ]
                session.lectureCards[selectedClass] << card
                if (!session.originalLectureCards[selectedClass]) {
                    session.originalLectureCards[selectedClass] = []
                }
                session.originalLectureCards[selectedClass] << card.clone()
            } else {
                // For Labs and Tutorials, create separate cards for each batch
                def batches = details.batch ? [details.batch] : ['1', '2', '3']
                batches.each { batchNumber ->
                    def card = cardBase + [
                        id: "${details.subject}_${details.class}_${details.type}_Batch${batchNumber}".toString(),
                        batch: batchNumber
                    ]
                    session.lectureCards[selectedClass] << card
                    if (!session.originalLectureCards[selectedClass]) {
                        session.originalLectureCards[selectedClass] = []
                    }
                    session.originalLectureCards[selectedClass] << card.clone()
                }
            }
        }
    }

    def resetTimetable() {
        def selectedClass = params.selectedClass ?: CLASSES[0]

        // Clear only the timetable entries but keep track of assignments
        def assignedLectures = [:]
        Set<String> countedLabSlots = [] // Track counted lab slots
        
        session.timetable?.each { classGroup, classTimetable ->
            assignedLectures[classGroup] = []
            
            classTimetable.each { day, daySlots ->
                daySlots.each { time, slots ->
                    slots.each { session ->
                        if (session.type == 'Lab') {
                            // For labs, only count if we haven't counted this slot's pair
                            String slotIdentifier = "${day}_${time}_${session.subject}_${session.batch}"
                            if (!countedLabSlots.contains(slotIdentifier)) {
                                assignedLectures[classGroup] << session
                                // Add both this slot and the next slot to counted slots
                                countedLabSlots.add(slotIdentifier)
                                def nextTimeIndex = TIME_SLOTS.indexOf(time) + 1
                                if (nextTimeIndex < TIME_SLOTS.size()) {
                                    def nextTime = TIME_SLOTS[nextTimeIndex]
                                    countedLabSlots.add("${day}_${nextTime}_${session.subject}_${session.batch}")
                                }
                            }
                        } else {
                            assignedLectures[classGroup] << session
                        }
                    }
                }
            }
        }

        // Reset timetable
        session.timetable = [:]
        CLASSES.each { classGroup ->
            session.timetable[classGroup] = [:]
            WEEK_DAYS.each { day ->
                session.timetable[classGroup][day] = [:]
                TIME_SLOTS.each { time ->
                    session.timetable[classGroup][day][time] = []
                }
            }
        }
        
        // Reset lecture cards while preserving counts
        session.lectureCards = [:]
        CLASSES.each { classGroup ->
            generateLectureCards(classGroup)
            
            // Update counts based on previous assignments
            if (assignedLectures[classGroup]) {
                session.lectureCards[classGroup].each { card ->
                    def assignedCount = assignedLectures[classGroup].count { assigned ->
                        assigned.subject == card.subject && 
                        assigned.type == card.type &&
                        assigned.batch == card.batch
                    }
                    card.count = card.totalLectures - assignedCount
                }
            }
        }

        flash.message = "Timetable reset successfully"

        // Maintain the current page/step
        if (request.xhr) {
            render([success: true, message: flash.message] as JSON)
        } else {
            redirect(action: "index", params: [currentStep: params.currentStep, selectedClass: selectedClass])
        }
    }

    def addSubject() {
        if (!session.subjectDetails) {
            session.subjectDetails = [:]
        }
        
        def type = params.type
        def batch = (type == 'Lab' || type == 'Tutorial') ? params.int('batch') : null
        def key = "${params.subject}_${params.class}_${type}_${batch ?: 'NoBatch'}"
        
        int lecturesPerWeek = params.int('lecturesPerWeek')
        
        session.subjectDetails[key] = [
            subject: params.subject,
            teacher: params.teacher,
            class: params.class,
            lecturesPerWeek: lecturesPerWeek,
            type: type,
            batch: batch,
            roomAllocation: params.roomAllocation,
            manualRoom: params.roomAllocation == 'manual' ? params.manualRoom : null
        ]
        
        generateLectureCards(params.class)

        resetTimetable()
        
        redirect(action: "index", params: [selectedClass: params.class])
    }

    def assignLecture() {
        log.info("Assigning lecture: ${request.JSON}")
        try {
            def data = request.JSON
            def selectedClass = data.selectedClass ?: params.selectedClass
            def lectureId = data.lectureId
            def day = data.day
            def time = data.time

            if (!selectedClass || !lectureId || !day || !time) {
                log.warn("Missing required data for lecture assignment")
                render([success: false, message: "Missing required data for lecture assignment"] as JSON)
                return
            }

            def timetable = session.timetable[selectedClass] ?: [:]
            timetable[day] = timetable[day] ?: [:]
            timetable[day][time] = timetable[day][time] ?: []

            def lecture = session.lectureCards[selectedClass]?.find { it.id == lectureId }

            if (!lecture) {
                render([success: false, message: "Lecture not found"] as JSON)
                return
            }

            // First check teacher constraints
            if (!isTeacherAvailablePerConstraints(lecture.teacher, day, time)) {
                render([success: false, message: "Teacher is not available at this time according to their constraints"] as JSON)
                return
            }

            // Then check other availability
            if (!isTeacherAvailable(selectedClass, day, time, lecture.teacher)) {
                render([success: false, message: "Teacher is not available at this time"] as JSON)
                return
            }

            if (canAddLectureToSlot(timetable, day, time, lecture, selectedClass)) {
                def subjectKey = "${lecture.subject}_${selectedClass}_${lecture.type}_${lecture.batch ?: 'NoBatch'}"
                def lectureDetails = session.subjectDetails[subjectKey]
                
                def newLecture = [
                    subject: lecture.subject,
                    teacher: lecture.teacher,
                    room: assignRoom(selectedClass, day, time, lecture.type, lectureDetails),
                    type: lecture.type,
                    batch: lecture.batch
                ]
                
                timetable[day][time] << newLecture

                if (lecture.type == "Lab") {
                    def nextTimeIndex = TIME_SLOTS.indexOf(time) + 1
                    if (nextTimeIndex < TIME_SLOTS.size()) {
                        def nextTime = TIME_SLOTS[nextTimeIndex]
                        if (!isTeacherAvailablePerConstraints(lecture.teacher, day, nextTime)) {
                            // Rollback the assignment if next slot isn't available
                            timetable[day][time].remove(newLecture)
                            render([success: false, message: "Cannot assign lab - teacher not available for the required consecutive slot"] as JSON)
                            return
                        }
                        timetable[day][nextTime] = timetable[day][nextTime] ?: []
                        // Clone the lecture for the next slot
                        timetable[day][nextTime] << newLecture.clone()
                    }
                }

                // Decrement count only once, regardless of whether it's a lab or not
                lecture.count--
                
                session.timetable[selectedClass] = timetable
                render([success: true, message: "Lecture assigned successfully", 
                    lecture: newLecture, 
                    nextSlot: lecture.type == "Lab" ? TIME_SLOTS[TIME_SLOTS.indexOf(time) + 1] : null,
                    remainingCount: lecture.count] as JSON)
            } else {
                String errorMessage = lecture.type == "Lecture" ?
                    "Cannot add lecture to this slot" :
                    "Cannot add ${lecture.type} to this slot (requires two consecutive empty slots for Labs)"
                render([success: false, message: errorMessage] as JSON)
            }
        } catch (Exception e) {
            log.error("Error during lecture assignment", e)
            render([success: false, message: "Error assigning lecture: ${e.message}"] as JSON)
        }
    }

    def generateTimetable() {
        try {
            def selectedClass = params.selectedClass
            if (!selectedClass) {
                render([success: false, message: "No class selected"] as JSON)
                return
            }

            def allLectureCards = session.lectureCards[selectedClass] ?: []
            if (allLectureCards.isEmpty()) {
                render([success: false, message: "No lecture cards found for the selected class"] as JSON)
                return
            }

            // Preserve existing timetable assignments
            def timetable = [:] 
            if (session.timetable?[selectedClass]) {
                // Clone existing timetable structure
                timetable = session.timetable[selectedClass].collectEntries { day, slots ->
                    [(day): slots.collectEntries { time, assignments ->
                        [(time): assignments.collect { it.clone() }]
                    }]
                }
            } else {
                // Initialize new timetable if none exists
                WEEK_DAYS.each { day ->
                    timetable[day] = [:]
                    TIME_SLOTS.each { time ->
                        timetable[day][time] = []
                    }
                }
            }

            // Create a working copy of cards that have remaining lectures
            def lectureCardsToAssign = allLectureCards.findAll { it.count > 0 }.collect { it.clone() }
            
            // Sort working copy to prioritize labs and less flexible sessions
            lectureCardsToAssign.sort { a, b ->
                def typeOrder = ['Lab': 1, 'Tutorial': 2, 'Lecture': 3]
                return typeOrder[a.type] <=> typeOrder[b.type]
            }

            def unassignedSessions = []
            
            lectureCardsToAssign.each { card ->
                def remainingLectures = card.count
                while (remainingLectures > 0) {
                    def slot = findRandomAvailableSlot(timetable, card, selectedClass)
                    if (slot) {
                        def subjectKey = "${card.subject}_${selectedClass}_${card.type}_${card.batch ?: 'NoBatch'}"
                        def lecture = [
                            subject: card.subject,
                            teacher: card.teacher,
                            class: selectedClass,
                            room: assignRoom(selectedClass, slot.day, slot.time, card.type, session.subjectDetails[subjectKey]),
                            type: card.type,
                            batch: card.batch
                        ]

                        // Add lecture to current slot
                        timetable[slot.day][slot.time] << lecture
                        
                        // For labs, add to next slot but don't decrement count again
                        if (card.type == "Lab") {
                            def nextTimeIndex = TIME_SLOTS.indexOf(slot.time) + 1
                            if (nextTimeIndex < TIME_SLOTS.size()) {
                                def nextTime = TIME_SLOTS[nextTimeIndex]
                                timetable[slot.day][nextTime] << lecture.clone()
                            }
                        }
                        
                        remainingLectures--
                        
                        // Update the count in the original card list only once per assignment
                        def originalCard = allLectureCards.find { it.id == card.id }
                        if (originalCard) {
                            originalCard.count--
                        }
                    } else {
                        unassignedSessions << [
                            subject: card.subject,
                            teacher: card.teacher,
                            type: card.type,
                            remainingCount: remainingLectures
                        ]
                        break
                    }
                }
            }

            session.timetable[selectedClass] = timetable
            session.lectureCards[selectedClass] = allLectureCards

            def response = [success: true, timetable: timetable, lectureCards: allLectureCards]
            if (unassignedSessions) {
                response.warnings = ["Some sessions could not be assigned due to constraints:"]
                unassignedSessions.each { session ->
                    response.warnings << "${session.subject} (${session.type}) - ${session.remainingCount} sessions remaining"
                }
            }

            render response as JSON
        } catch (Exception e) {
            log.error("Error generating timetable: ${e.message}", e)
            render([success: false, message: "Error generating timetable: ${e.message}"] as JSON)
        }
    }

    // Modified method: returns the first available slot instead of a random one
    private Map findRandomAvailableSlot(Map timetable, Map card, String selectedClass) {
        def availableSlots = []
        WEEK_DAYS.each { day ->
            TIME_SLOTS.each { time ->
                if (canAddLectureToSlot(timetable, day, time, card, selectedClass) && 
                    isTeacherAvailable(selectedClass, day, time, card.teacher)) {
                    availableSlots << [day: day, time: time]
                }
            }
        }
        // Return the first available slot instead of a random one
        return availableSlots ? availableSlots[0] : null
    }

    def resetTimetableWithoutRedirect() {
        session.timetable = [:]
        CLASSES.each { classGroup ->
            session.timetable[classGroup] = [:]
            WEEK_DAYS.each { day ->
                session.timetable[classGroup][day] = [:]
                TIME_SLOTS.each { time ->
                    session.timetable[classGroup][day][time] = []
                }
            }
        }
        session.lectureCards = [:]
        CLASSES.each { classGroup ->
            generateLectureCards(classGroup)
        }
    }

    def deleteSubject() {
        def key = params.key
        if (session.subjectDetails?.containsKey(key)) {
            session.subjectDetails.remove(key)

            resetTimetable()

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

    def getTimetableForEntity() {
        def type = params.type
        def value = params.value
        def timetable = [:]

        // Logic to fetch the timetable based on the type and value
        switch(type) {
            case 'option1': // Class
                timetable = session.timetable[value] ?: [:]
                break
            case 'option2': // Teacher
                timetable = fetchTimetableForTeacher(value)
                break
            case 'option3': // Room (now includes both classrooms and tutorial rooms)
                timetable = fetchTimetableForRoom(value)
                break
            default:
                render([success: false, message: "Invalid entity type"] as JSON)
                return
        }

        render([success: true, timetable: timetable] as JSON)
    }

    private Map fetchTimetableForTeacher(String teacher) {
        def timetable = [:]
        session.timetable.each { className, classTimetable ->
            classTimetable.each { day, daySlots ->
                daySlots.each { time, sessions ->
                    sessions.each { session ->
                        if (session.teacher == teacher) {
                            timetable[day] = timetable[day] ?: [:]
                            timetable[day][time] = timetable[day][time] ?: []
                            timetable[day][time] << session + [class: className]
                        }
                    }
                }
            }
        }
        return timetable
    }

    private Map fetchTimetableForRoom(String room) {
        def timetable = [:]
        session.timetable.each { className, classTimetable ->
            classTimetable.each { day, daySlots ->
                daySlots.each { time, sessions ->
                    sessions.each { session ->
                        if (session.room == room) {
                            timetable[day] = timetable[day] ?: [:]
                            timetable[day][time] = timetable[day][time] ?: []
                            timetable[day][time] << session + [class: className]
                        }
                    }
                }
            }
        }
        return timetable
    }

    def getCalendarLinks() {
        def selectedClass = params.selectedClass
        def startDate = params.startDate
        def endDate = params.endDate
        def timetable = session.timetable[selectedClass]
        
        def calendarUrl = generateCalendarUrl(timetable, selectedClass, startDate, endDate)
        
        render([success: true, url: calendarUrl] as JSON)
    }

    private String generateCalendarUrl(Map timetable, String className, String startDateStr, String endDateStr) {
        // Parse dates using SimpleDateFormat
        def sdf = new SimpleDateFormat("yyyy-MM-dd")
        def formatDateTime = new SimpleDateFormat("yyyyMMdd'T'HHmmss")
        def startDate = sdf.parse(startDateStr)
        def endDate = sdf.parse(endDateStr)
        
        def calendar = Calendar.getInstance()
        calendar.setTime(startDate)
        
        def events = []
        
        timetable.each { day, slots ->
            slots.each { time, sessions ->
                sessions.each { session ->
                    // Set event time
                    def eventCal = calendar.clone() as Calendar
                    
                    // Move to correct day of week
                    while (eventCal.get(Calendar.DAY_OF_WEEK) != getDayNumber(day)) {
                        eventCal.add(Calendar.DATE, 1)
                    }
                    
                    // Set time
                    def (hour, minute) = time.split(':').collect { it.toInteger() }
                    eventCal.set(Calendar.HOUR_OF_DAY, hour)
                    eventCal.set(Calendar.MINUTE, minute)
                    eventCal.set(Calendar.SECOND, 0)
                    
                    def startTime = eventCal.clone() as Calendar
                    def endTime = eventCal.clone() as Calendar
                    
                    // Set duration
                    if (session.type == 'Lab') {
                        endTime.add(Calendar.HOUR_OF_DAY, 2)
                    } else {
                        endTime.add(Calendar.HOUR_OF_DAY, 1)
                    }
                    
                    // Calculate until date for recurrence (end date)
                    def untilStr = formatDateTime.format(endDate)
                    
                    // Format for Google Calendar
                    def details = """Teacher: ${session.teacher}
                        Room: ${session.room}
                        ${session.batch ? 'Batch: ' + session.batch : ''}
                        Class: ${className}"""

                    events << [
                        text: "${session.subject} (${session.type})",
                        details: details.toString().trim(),
                        location: session.room,
                        start: formatDateTime.format(startTime.time),
                        end: formatDateTime.format(endTime.time),
                        recur: "FREQ=WEEKLY;UNTIL=${untilStr}"
                    ]
                }
            }
        }
        
        // Generate single URL for all events
        def baseUrl = 'https://calendar.google.com/calendar/render'
        def params = new StringBuilder()
        
        events.eachWithIndex { event, index ->
            params.append(index == 0 ? '?' : '&')
            params.append("action=TEMPLATE")
            params.append("&text=${URLEncoder.encode(event.text, 'UTF-8')}")
            params.append("&details=${URLEncoder.encode(event.details, 'UTF-8')}")
            params.append("&location=${URLEncoder.encode(event.location, 'UTF-8')}")
            params.append("&dates=${event.start}/${event.end}")
            params.append("&recur=RRULE:${event.recur}")
        }
        
        return baseUrl + params.toString()
    }

    private int getDayNumber(String day) {
        def dayMap = [
            'Monday': Calendar.MONDAY,
            'Tuesday': Calendar.TUESDAY,
            'Wednesday': Calendar.WEDNESDAY,
            'Thursday': Calendar.THURSDAY,
            'Friday': Calendar.FRIDAY,
            'Saturday': Calendar.SATURDAY,
            'Sunday': Calendar.SUNDAY
        ]
        return dayMap[day]
    }

    def saveTeacherConstraints() {
        def data = request.JSON
        def teacher = data.teacher
        def workingDays = data.workingDays
        def availableSlots = data.availableSlots

        try {
            // Initialize teacher constraints in session if not exists
            if (!session.teacherConstraints) {
                session.teacherConstraints = [:]
            }

            // Store constraints for this teacher
            session.teacherConstraints[teacher] = [
                workingDays: workingDays,
                availableSlots: availableSlots
            ]

            render([success: true, message: "Constraints saved successfully"] as JSON)
        } catch (Exception e) {
            log.error("Error saving constraints", e)
            render([success: false, message: "Error saving constraints: ${e.message}"] as JSON)
        }
    }

    def getTeacherConstraints() {
        def teacher = params.teacher
        
        try {
            def constraints = session.teacherConstraints?[teacher]
            
            if (constraints) {
                log.info("Found constraints for teacher ${teacher}: ${constraints}")
                render([
                    success: true,
                    workingDays: constraints.workingDays,
                    availableSlots: constraints.availableSlots
                ] as JSON)
            } else {
                // Return all slots enabled if no constraints exist
                def allSlotsEnabled = [:]
                WEEK_DAYS.each { day ->
                    allSlotsEnabled[day] = TIME_SLOTS.collect()  // Copy all time slots
                }
                
                render([
                    success: true,
                    workingDays: WEEK_DAYS,  // All days enabled
                    availableSlots: allSlotsEnabled  // All slots enabled
                ] as JSON)
            }
        } catch (Exception e) {
            log.error("Error getting constraints", e)
            render([success: false, message: "Error getting constraints: ${e.message}"] as JSON)
        }
    }

    private boolean isTeacherAvailablePerConstraints(String teacher, String day, String time) {
        // Get teacher constraints from session
        def constraints = session.teacherConstraints?[teacher]
        
        if (!constraints) {
            // If no constraints set, teacher is available
            return true
        }
        
        // Check if the day and time slot are in the available slots
        def availableSlots = constraints.availableSlots
        return availableSlots[day]?.contains(time) ?: false
    }

    def saveClassConstraints() {
        def data = request.JSON
        def classGroup = data.classGroup
        def workingDays = data.workingDays
        def availableSlots = data.availableSlots

        try {
            // Initialize class constraints in session if not exists
            if (!session.classConstraints) {
                session.classConstraints = [:]
            }

            // Store constraints for this class
            session.classConstraints[classGroup] = [
                workingDays: workingDays,
                availableSlots: availableSlots
            ]

            render([success: true, message: "Class constraints saved successfully"] as JSON)
        } catch (Exception e) {
            log.error("Error saving class constraints", e)
            render([success: false, message: "Error saving constraints: ${e.message}"] as JSON)
        }
    }

    def getClassConstraints() {
        def classGroup = params.classGroup
        
        try {
            def constraints = session.classConstraints?[classGroup]
            
            if (constraints) {
                log.info("Found constraints for class ${classGroup}: ${constraints}")
                render([
                    success: true,
                    workingDays: constraints.workingDays,
                    availableSlots: constraints.availableSlots
                ] as JSON)
            } else {
                // Return all slots enabled if no constraints exist
                def allSlotsEnabled = [:]
                WEEK_DAYS.each { day ->
                    allSlotsEnabled[day] = TIME_SLOTS.collect()
                }
                
                render([
                    success: true,
                    workingDays: WEEK_DAYS,
                    availableSlots: allSlotsEnabled
                ] as JSON)
            }
        } catch (Exception e) {
            log.error("Error getting class constraints", e)
            render([success: false, message: "Error getting constraints: ${e.message}"] as JSON)
        }
    }

    // Add this helper method
    private boolean isClassAvailablePerConstraints(String classGroup, String day, String time) {
        def constraints = session.classConstraints?[classGroup]
        if (!constraints) {
            return true
        }
        return constraints.availableSlots[day]?.contains(time) ?: false
    }

    def downloadTimetable() {
        try {
            XSSFWorkbook workbook = new XSSFWorkbook()
            
            // Create a sheet for each class
            CLASSES.each { className ->
                XSSFSheet sheet = workbook.createSheet(className)
                def timetableData = session.timetable[className]
                
                // Create header row with time slots
                XSSFRow headerRow = sheet.createRow(0)
                headerRow.createCell(0).setCellValue("Day/Time")
                TIME_SLOTS.eachWithIndex { timeSlot, index ->
                    headerRow.createCell(index + 1).setCellValue(timeSlot)
                }
                
                // Add day rows
                WEEK_DAYS.eachWithIndex { day, rowIndex ->
                    XSSFRow row = sheet.createRow(rowIndex + 1)
                    row.createCell(0).setCellValue(day)
                    
                    // Variable to track maximum lines in this row
                    int maxLinesInRow = 1
                    
                    // Add time slot cells
                    TIME_SLOTS.eachWithIndex { timeSlot, colIndex ->
                        XSSFCell cell = row.createCell(colIndex + 1)
                        def sessions = timetableData?[day]?[timeSlot] ?: []
                        
                        if (sessions) {
                            StringBuilder cellContent = new StringBuilder()
                            boolean firstSession = true
                            
                            sessions.each { session ->
                                if (!firstSession) {
                                    cellContent.append("\n----------------------------------------\n")
                                }
                                firstSession = false
                                
                                if (session.type in ['Lab', 'Tutorial']) {
                                    cellContent.append("Batch ${session.batch ?: 'N/A'}\n")
                                }
                                
                                cellContent.append("Subject: ${session.subject ?: 'N/A'}\n")
                                cellContent.append("Teacher: ${session.teacher ?: 'N/A'}\n")
                                cellContent.append("Room: ${session.room ?: 'TBD'}\n")
                                cellContent.append("Type: ${session.type ?: 'N/A'}")
                                
                                if (session.type == 'Lab') {
                                    cellContent.append(" (2 hours)")
                                }
                            }
                            
                            String content = cellContent.toString()
                            cell.setCellValue(content)
                            
                            // Count number of lines in this cell
                            int linesInCell = content.count("\n") + 1
                            maxLinesInRow = Math.max(maxLinesInRow, linesInCell)
                        } else {
                            cell.setCellValue("-")
                        }
                    }
                    
                    // Set row height based on content (approximately 300 units per line)
                    row.setHeight((short)(maxLinesInRow * 300))
                }
                
                // Auto-size columns for width
                (0..TIME_SLOTS.size()).each { sheet.autoSizeColumn(it) }
                
                // Header style
                CellStyle headerStyle = workbook.createCellStyle()
                headerStyle.setAlignment(HorizontalAlignment.CENTER)
                headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex())
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
                headerStyle.setWrapText(true)
                
                // Apply header style
                XSSFRow header = sheet.getRow(0)
                (0..TIME_SLOTS.size()).each { colIndex ->
                    XSSFCell cell = header.getCell(colIndex)
                    if (cell) {
                        cell.setCellStyle(headerStyle)
                    }
                }
                
                // Day column style
                CellStyle dayStyle = workbook.createCellStyle()
                dayStyle.setAlignment(HorizontalAlignment.LEFT)
                dayStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex())
                dayStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
                dayStyle.setWrapText(true)
                
                // Apply day style
                (1..WEEK_DAYS.size()).each { rowIndex ->
                    XSSFCell cell = sheet.getRow(rowIndex).getCell(0)
                    cell.setCellStyle(dayStyle)
                }
                
                // Content style with increased font size
                CellStyle contentStyle = workbook.createCellStyle()
                contentStyle.setWrapText(true)
                contentStyle.setVerticalAlignment(VerticalAlignment.TOP)
                contentStyle.setAlignment(HorizontalAlignment.LEFT)
                
                Font contentFont = workbook.createFont()
                contentFont.setFontHeightInPoints((short) 11) // Increased font size
                contentStyle.setFont(contentFont)
                
                // Apply content style
                (1..WEEK_DAYS.size()).each { rowIndex ->
                    XSSFRow row = sheet.getRow(rowIndex)
                    (1..TIME_SLOTS.size()).each { colIndex ->
                        XSSFCell cell = row.getCell(colIndex)
                        if (cell) {
                            cell.setCellStyle(contentStyle)
                        }
                    }
                }
                
                // Set a minimum width for all columns
                (0..TIME_SLOTS.size()).each { colIndex ->
                    int currentWidth = sheet.getColumnWidth(colIndex)
                    if (currentWidth < 6000) { // Minimum width of 6000 units
                        sheet.setColumnWidth(colIndex, 6000)
                    }
                }
            }

            // Set response headers and write to output
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            response.setHeader("Content-Disposition", "attachment; filename=timetable.xlsx")
            workbook.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            workbook.close()
            
        } catch (Exception e) {
            log.error("Error generating timetable Excel file", e)
            flash.message = "Error generating timetable: ${e.message}"
            redirect(action: "index")
        }
    }

    def downloadEntityTimetable() {
        try {
            def type = params.type
            def value = params.value
            def timetable
            def fileName
            
            switch(type) {
                case 'option1': // Class
                    timetable = session.timetable[value] ?: [:]
                    fileName = "Class_${value}_Timetable.xlsx"
                    break
                case 'option2': // Teacher
                    timetable = fetchTimetableForTeacher(value)
                    fileName = "Teacher_${value}_Timetable.xlsx"
                    break
                case 'option3': // Room
                    timetable = fetchTimetableForRoom(value)
                    fileName = "Room_${value}_Timetable.xlsx"
                    break
                default:
                    throw new Exception("Invalid entity type")
            }

            XSSFWorkbook workbook = new XSSFWorkbook()
            XSSFSheet sheet = workbook.createSheet("Timetable")
            
            // Create header row with time slots
            XSSFRow headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("Day/Time")
            TIME_SLOTS.eachWithIndex { timeSlot, index ->
                headerRow.createCell(index + 1).setCellValue(timeSlot)
            }
            
            // Add day rows
            WEEK_DAYS.eachWithIndex { day, rowIndex ->
                XSSFRow row = sheet.createRow(rowIndex + 1)
                row.createCell(0).setCellValue(day)
                
                // Variable to track maximum lines in this row
                int maxLinesInRow = 1
                
                // Add time slot cells
                TIME_SLOTS.eachWithIndex { timeSlot, colIndex ->
                    XSSFCell cell = row.createCell(colIndex + 1)
                    def sessions = timetable[day]?[timeSlot] ?: []
                    
                    if (sessions) {
                        StringBuilder cellContent = new StringBuilder()
                        boolean firstSession = true
                        
                        sessions.each { session ->
                            if (!firstSession) {
                                cellContent.append("\n----------------------------------------\n")
                            }
                            firstSession = false
                            
                            // Add class info for teacher and room views
                            if (type != 'option1') {
                                cellContent.append("Class: ${session.class ?: 'N/A'}\n")
                            }
                            
                            if (session.type in ['Lab', 'Tutorial']) {
                                cellContent.append("Batch ${session.batch ?: 'N/A'}\n")
                            }
                            
                            cellContent.append("Subject: ${session.subject ?: 'N/A'}\n")
                            
                            // Show teacher except in teacher view
                            if (type != 'option2') {
                                cellContent.append("Teacher: ${session.teacher ?: 'N/A'}\n")
                            }
                            
                            // Show room except in room view
                            if (type != 'option3') {
                                cellContent.append("Room: ${session.room ?: 'TBD'}\n")
                            }
                            
                            cellContent.append("Type: ${session.type ?: 'N/A'}")
                            
                            if (session.type == 'Lab') {
                                cellContent.append(" (2 hours)")
                            }
                        }
                        
                        String content = cellContent.toString()
                        cell.setCellValue(content)
                        
                        // Count number of lines in this cell
                        int linesInCell = content.count("\n") + 1
                        maxLinesInRow = Math.max(maxLinesInRow, linesInCell)
                    } else {
                        cell.setCellValue("-")
                    }
                }
                
                // Set row height based on content
                row.setHeight((short)(maxLinesInRow * 300))
            }
            
            // Style the sheet
            // Header style
            CellStyle headerStyle = workbook.createCellStyle()
            headerStyle.setAlignment(HorizontalAlignment.CENTER)
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex())
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
            headerStyle.setWrapText(true)
            
            Font headerFont = workbook.createFont()
            headerFont.setBold(true)
            headerStyle.setFont(headerFont)
            
            // Apply header style
            XSSFRow header = sheet.getRow(0)
            (0..TIME_SLOTS.size()).each { colIndex ->
                XSSFCell cell = header.getCell(colIndex)
                if (cell) cell.setCellStyle(headerStyle)
            }
            
            // Day column style
            CellStyle dayStyle = workbook.createCellStyle()
            dayStyle.setAlignment(HorizontalAlignment.LEFT)
            dayStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex())
            dayStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
            dayStyle.setWrapText(true)
            dayStyle.setFont(headerFont)
            
            // Apply day style
            (1..WEEK_DAYS.size()).each { rowIndex ->
                XSSFCell cell = sheet.getRow(rowIndex).getCell(0)
                cell.setCellStyle(dayStyle)
            }
            
            // Content style
            CellStyle contentStyle = workbook.createCellStyle()
            contentStyle.setWrapText(true)
            contentStyle.setVerticalAlignment(VerticalAlignment.TOP)
            contentStyle.setAlignment(HorizontalAlignment.LEFT)
            
            Font contentFont = workbook.createFont()
            contentFont.setFontHeightInPoints((short) 11)
            contentStyle.setFont(contentFont)
            
            // Apply content style and set column widths
            (1..WEEK_DAYS.size()).each { rowIndex ->
                XSSFRow row = sheet.getRow(rowIndex)
                (1..TIME_SLOTS.size()).each { colIndex ->
                    XSSFCell cell = row.getCell(colIndex)
                    if (cell) cell.setCellStyle(contentStyle)
                }
            }
            
            // Auto-size and set minimum width for all columns
            (0..TIME_SLOTS.size()).each { colIndex ->
                sheet.autoSizeColumn(colIndex)
                int currentWidth = sheet.getColumnWidth(colIndex)
                if (currentWidth < 6000) {
                    sheet.setColumnWidth(colIndex, 6000)
                }
            }

            // Set response headers and write to output
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            response.setHeader("Content-Disposition", "attachment; filename=${fileName}")
            workbook.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            workbook.close()
            
        } catch (Exception e) {
            log.error("Error generating entity timetable Excel file", e)
            flash.message = "Error generating timetable: ${e.message}"
            redirect(action: "index", params: [currentStep: 3])
        }
    }

    private Map filterSubjectDetailsByClass(String classGroup) {
        return (session.subjectDetails ?: [:]).findAll { key, value -> value.class == classGroup } ?: [:]
    }

    private Map filterTimetableByClass(String classGroup) {
        session.timetable?[classGroup] ?: [:]
    }

    def downloadTemplate() {
        XSSFWorkbook workbook = new XSSFWorkbook()
        XSSFSheet sheet = workbook.createSheet("Subject Mapping")

        // Create header row
        XSSFRow headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("Subject")
        headerRow.createCell(1).setCellValue("Type")
        headerRow.createCell(2).setCellValue("Class")
        headerRow.createCell(3).setCellValue("Batch")
        headerRow.createCell(4).setCellValue("Teacher")
        headerRow.createCell(5).setCellValue("Room")
        headerRow.createCell(6).setCellValue("Lectures Per Week")

        // Create dropdowns
        XSSFDataValidationHelper dvHelper = new XSSFDataValidationHelper(sheet)

        // Subject dropdown
        addDropdownValidation(sheet, dvHelper, 0, SUBJECTS)

        // Type dropdown
        addDropdownValidation(sheet, dvHelper, 1, ["Lecture", "Lab", "Tutorial"])

        // Class dropdown
        addDropdownValidation(sheet, dvHelper, 2, CLASSES)

        // Batch dropdown (only for Lab/Tutorial)
        addDropdownValidation(sheet, dvHelper, 3, ["", "1", "2", "3"])

        // Teacher dropdown
        addDropdownValidation(sheet, dvHelper, 4, TEACHERS)

        // Room Allocation dropdown
        List<String> allRooms = [""] + CLASSROOMS + LABROOMS + TUTORIALROOMS
        addDropdownValidation(sheet, dvHelper, 5, allRooms)
        
        // Set response headers
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        response.setHeader("Content-Disposition", "attachment; filename=subject_mapping_template.xlsx")

        // Write to output stream
        workbook.write(response.outputStream)
        response.outputStream.flush()
        response.outputStream.close()
        workbook.close()
    }

    private void addDropdownValidation(XSSFSheet sheet, XSSFDataValidationHelper dvHelper, int columnIndex, List<String> items) {
        CellRangeAddressList addressList = new CellRangeAddressList(1, 1000, columnIndex, columnIndex)
        
        if (items.join(",").length() <= 255) {
            // If the list is short enough, use the standard method
            XSSFDataValidationConstraint dvConstraint = (XSSFDataValidationConstraint) dvHelper.createExplicitListConstraint(items as String[])
            XSSFDataValidation validation = (XSSFDataValidation) dvHelper.createValidation(dvConstraint, addressList)
            sheet.addValidationData(validation)
        } else {
            // If the list is too long, create a named range and refer to it
            String rangeName = "ValidList_$columnIndex"
            XSSFName namedRange = sheet.getWorkbook().createName()
            namedRange.setNameName(rangeName)
            
            // Create a new sheet for the list
            XSSFSheet listSheet = sheet.getWorkbook().createSheet("_" + rangeName)
            for (int i = 0; i < items.size(); i++) {
                listSheet.createRow(i).createCell(0).setCellValue(items[i])
            }
            
            // Set the reference to the list
            String reference = "'" + listSheet.getSheetName() + "'!\$A\$1:\$A\$" + items.size()
            namedRange.setRefersToFormula(reference)
            
            // Create the validation constraint
            XSSFDataValidationConstraint dvConstraint = (XSSFDataValidationConstraint) dvHelper.createFormulaListConstraint(rangeName)
            XSSFDataValidation validation = (XSSFDataValidation) dvHelper.createValidation(dvConstraint, addressList)
            sheet.addValidationData(validation)
        }
    }

    def uploadSubjectMapping() {
        def file = request.getFile('excelFile')
        if (file.empty) {
            flash.message = "Please select a file"
            redirect(action: "index")
            return
        }

        try {
            XSSFWorkbook workbook = new XSSFWorkbook(file.inputStream)
            XSSFSheet sheet = workbook.getSheetAt(0)

            // Initialize session.subjectDetails if it's null
            if (session.subjectDetails == null) {
                session.subjectDetails = [:]
            }

            // Skip header row
            for (int i = 1; i <= sheet.lastRowNum; i++) {
                XSSFRow row = sheet.getRow(i)
                if (row == null) continue

                def subject = getCellValueAsString(row.getCell(0))
                def type = getCellValueAsString(row.getCell(1))
                def classGroup = getCellValueAsString(row.getCell(2))
                def batch = getCellValueAsString(row.getCell(3))
                def teacher = getCellValueAsString(row.getCell(4))
                def room = getCellValueAsString(row.getCell(5))
                def lecturesPerWeek = row.getCell(6)?.numericCellValue?.intValue()

                if (subject && type && classGroup && teacher && lecturesPerWeek != null) {
                    // Ignore batch for Lecture type
                    if (type == 'Lecture') {
                        batch = null
                    }

                    def key = "${subject}_${classGroup}_${type}_${batch ?: 'NoBatch'}"
                    
                    def entry = [
                        subject: subject,
                        teacher: teacher,
                        class: classGroup,
                        lecturesPerWeek: lecturesPerWeek,
                        type: type,
                        batch: batch,
                        roomAllocation: room ? 'manual' : 'automatic',
                        manualRoom: room
                    ]

                    session.subjectDetails[key] = entry
                }
            }

            workbook.close()
            flash.message = "Subject mapping uploaded successfully"
            
            // Reset the timetable after uploading new subject mappings
            resetTimetableWithoutRedirect()
        } catch (Exception e) {
            log.error("Error processing Excel file: ${e.message}", e)
            flash.message = "Error processing Excel file: ${e.message}"
        }

        redirect(action: "index")
    }

    // Helper method to get cell value as string
    private String getCellValueAsString(XSSFCell cell) {
        if (cell == null) return null
        switch (cell.cellType) {
            case CellType.STRING:
                return cell.stringCellValue
            case CellType.NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.dateCellValue.format("yyyy-MM-dd")
                }
                // Format numeric values to remove decimal places for whole numbers
                def value = cell.numericCellValue
                return (value == Math.floor(value)) ? String.valueOf(value.intValue()) : String.valueOf(value)
            case CellType.BOOLEAN:
                return String.valueOf(cell.booleanCellValue)
            case CellType.FORMULA:
                return cell.cellFormula
            default:
                return null
        }
    }

    private boolean checkConsecutiveLimit(String classGroup, String day, String time, String teacher) {
        def allTimetables = session.timetable

        int teacherConsecutive = 0
        int classConsecutive = 0
        int currentIndex = TIME_SLOTS.indexOf(time) - 1

        while (currentIndex >= 0) {
            def prevTime = TIME_SLOTS[currentIndex]

            // Check teacher continuity across all classes
            boolean teacherSessionPresent = false
            allTimetables.each { grp, grpTimetable ->
                def prevSlotSessions = grpTimetable[day]?[prevTime] ?: []
                if (prevSlotSessions.any { it.teacher == teacher }) {
                    teacherSessionPresent = true
                }
            }

            // Check class continuity for this specific class
            def classSlotSessions = allTimetables[classGroup][day][prevTime] ?: []
            boolean classSessionPresent = !classSlotSessions.isEmpty()

            if (teacherSessionPresent) {
                teacherConsecutive++
            } else {
                teacherConsecutive = 0
            }

            if (classSessionPresent) {
                classConsecutive++
            } else {
                classConsecutive = 0
            }

            // If teacher or class hit 4 consecutive sessions, must have a break
            if (teacherConsecutive == 4 || classConsecutive == 4) {
                return false
            }

            // If no continuity for both teacher and class, break out of the loop
            if (!teacherSessionPresent && !classSessionPresent) {
                break
            }

            currentIndex--
        }

        return true
    }

    private boolean isTeacherAvailable(String selectedClass, String day, String time, String teacher) {
        return !session.timetable.any { classGroup, classTimetable ->
            classTimetable[day]?[time]?.any { s -> s.teacher == teacher }
        }
    }

    private boolean canAddLectureToSlot(Map timetable, String day, String time, Map newLecture, String classGroup) {
        if (!isClassAvailablePerConstraints(classGroup, day, time)) return false
        if (!isTeacherAvailablePerConstraints(newLecture.teacher, day, time)) return false

        def currentSlot = timetable[day]?[time] ?: []

        // Before returning true, ensure consecutive limit is not violated:
        // Pass the classGroup and teacher to check globally for teacher and specifically for class.
        boolean canContinue = checkConsecutiveLimit(classGroup, day, time, newLecture.teacher)
        if (!canContinue) return false

        if (newLecture.type == "Lecture") {
            if (!currentSlot.isEmpty()) return false
            return true
        } else if (newLecture.type == "Lab") {
            def nextTimeIndex = TIME_SLOTS.indexOf(time) + 1
            if (nextTimeIndex >= TIME_SLOTS.size()) return false

            def nextTime = TIME_SLOTS[nextTimeIndex]
            def nextSlot = timetable[day]?[nextTime] ?: []

            if (!isClassAvailablePerConstraints(classGroup, day, nextTime)) return false
            if (!isTeacherAvailablePerConstraints(newLecture.teacher, day, nextTime)) return false
            if (!canAddBatchToSlot(currentSlot, newLecture)) return false
            if (!canAddBatchToSlot(nextSlot, newLecture)) return false
            if (isTeacherOrRoomOccupied(currentSlot, newLecture)) return false
            if (isTeacherOrRoomOccupied(nextSlot, newLecture)) return false

            // Re-check consecutive limit again might not be necessary here, since we only move forward if the first slot passed.
            return true
        } else if (newLecture.type == "Tutorial") {
            if (!canAddBatchToSlot(currentSlot, newLecture)) return false
            if (isTeacherOrRoomOccupied(currentSlot, newLecture)) return false
            return true
        }

        return false
    }

    private boolean canAddBatchToSlot(List slotSessions, Map newLecture) {
        // Check if this batch already exists in the slot for any subject
        if (slotSessions.any { it.batch == newLecture.batch }) {
            return false
        }
        
        // Check if there's space (max 3 batches)
        if (slotSessions.size() >= 3) {
            return false
        }
        
        // Check if there's already a lecture (no mixing with batches)
        if (slotSessions.any { it.type == "Lecture" }) {
            return false
        }
        
        return true
    }

    private boolean isTeacherOrRoomOccupied(List slotSessions, Map newLecture) {
        return slotSessions.any { session ->
            session.teacher == newLecture.teacher || 
            (session.room == newLecture.room) ||
            (isRoomTypeConflict(session, newLecture))
        }
    }

    private boolean isRoomTypeConflict(Map session, Map newLecture) {
        // If either session is a lab or tutorial, check for room type conflicts
        if (session.type in ['Lab', 'Tutorial'] || newLecture.type in ['Lab', 'Tutorial']) {
            def currentRoom = session.room
            def newRoom = newLecture.room
            
            boolean isCurrentLabRoom = LABROOMS.contains(currentRoom)
            boolean isNewLabRoom = LABROOMS.contains(newRoom)
            boolean isCurrentTutorialRoom = TUTORIALROOMS.contains(currentRoom)
            boolean isNewTutorialRoom = TUTORIALROOMS.contains(newRoom)
            
            return (isCurrentLabRoom && isNewLabRoom) || 
                (isCurrentTutorialRoom && isNewTutorialRoom)
        }
        return false
    }

    private String assignRoom(String selectedClass, String day, String time, String type, Map lectureDetails) {
        log.info("assignRoom called with: selectedClass=${selectedClass}, day=${day}, time=${time}, type=${type}, lectureDetails=${lectureDetails}")

        if (lectureDetails == null) {
            log.warn("lectureDetails is null, defaulting to automatic room allocation")
            return assignAutomaticRoom(type, day, time)
        }

        if (lectureDetails.roomAllocation == 'manual' && lectureDetails.manualRoom) {
            return lectureDetails.manualRoom
        }

        return assignAutomaticRoom(type, day, time)
    }

    private String assignAutomaticRoom(String type, String day, String time) {
        def occupiedRooms = getAllOccupiedRooms(day, time)
        def occupiedLabRooms = getOccupiedLabRooms(day, time)
        def occupiedTutorialRooms = getOccupiedTutorialRooms(day, time)

        switch (type) {
            case "Lecture":
                def availableRooms = CLASSROOMS - occupiedRooms
                return availableRooms ? availableRooms[0] : "TBD"
            
            case "Lab":
                // For labs, ensure we don't assign same lab room to different batches
                def availableLabRooms = LABROOMS - occupiedLabRooms
                return availableLabRooms ? availableLabRooms[0] : "TBD"
            
            case "Tutorial":
                // For tutorials, ensure we don't assign same tutorial room to different batches
                def availableTutorialRooms = TUTORIALROOMS - occupiedTutorialRooms
                return availableTutorialRooms ? availableTutorialRooms[0] : "TBD"
            
            default:
                return "TBD"
        }
    }

    private List<String> getAllOccupiedRooms(String day, String time) {
        session.timetable.values().collect { classTimetable ->
            classTimetable[day]?[time]?.collect { it.room }
        }.flatten().findAll()
    }

    private List<String> getOccupiedLabRooms(String day, String time) {
        session.timetable.values().collect { classTimetable ->
            classTimetable[day]?[time]?.findAll { it.type == 'Lab' }?.collect { it.room }
        }.flatten().findAll()
    }

    private List<String> getOccupiedTutorialRooms(String day, String time) {
        session.timetable.values().collect { classTimetable ->
            classTimetable[day]?[time]?.findAll { it.type == 'Tutorial' }?.collect { it.room }
        }.flatten().findAll()
    }
}
