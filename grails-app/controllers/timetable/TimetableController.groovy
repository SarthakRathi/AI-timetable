// File: grails-app/controllers/timetable/TimetableController.groovy
package timetable

import grails.converters.JSON
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.util.CellRangeAddressList
import org.apache.poi.xssf.usermodel.XSSFDataValidation
import org.apache.poi.xssf.usermodel.XSSFDataValidationConstraint
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper

class TimetableController {
    ExcelService excelService

    // Predefined lists
    private static final List<String> SUBJECTS = ['Math', 'Science', 'History', 'Art', 'Physical Education']
    private static final List<String> CLASSES = ['FY', 'SY', 'TY']
    private static final List<String> ROOMS = ['101', '102', '103', '104', '105']
    private static final List<String> LABROOMS = ['Lab1', 'Lab2', 'Lab3']
    private static final List<String> TEACHERS = ['Rohan', 'Ravi', 'Ashwin', 'Karan', 'Priya']
    private static final List<String> WEEK_DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday']
    private static final List<String> TIME_SLOTS = ['8:00', '09:00', '10:00', '11:00', '12:00', '1:00', '2:00', '3:00', '4:00', '5:00']
    private static final List<String> COLORS = ['#87A2FF', '#C4D7FF', '#FFD7C4', '#FFF4B5', '#E7CCCC', '#CDC1FF', '#CEDF9F', '#F1F3C2', '#FFC6C6', '#95D2B3']

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
        def colorMap = assignColorsToSubjects()

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
            rooms: ROOMS.collect { it.encodeAsJSON() },
            colorMap: colorMap  
        ]
    }

    private Map<String, String> assignColorsToSubjects() {
        if (!session.colorMap) {
            session.colorMap = [:]
        }
        if (session.subjectDetails) {
            def subjects = session.subjectDetails.values().collect { it.subject }.unique()
            subjects.each { subject ->
                if (!session.colorMap.containsKey(subject)) {
                    session.colorMap[subject] = COLORS[new Random().nextInt(COLORS.size())]
                }
            }
        }
        return session.colorMap
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
                if (details.type == "Lecture") {
                    session.lectureCards[selectedClass] << [
                        id: "${details.subject}_${details.class}_${details.type}".toString(),
                        subject: details.subject,
                        teacher: details.teacher,
                        count: remainingLectures,
                        type: details.type
                    ]
                } else {
                    // For Labs and Tutorials, create separate cards for each batch
                    (1..3).each { batchNumber ->
                        session.lectureCards[selectedClass] << [
                            id: "${details.subject}_${details.class}_${details.type}_Batch${batchNumber}".toString(),
                            subject: details.subject,
                            teacher: details.teacher,
                            count: remainingLectures,  // Each batch gets the full count
                            type: details.type,
                            batch: batchNumber
                        ]
                    }
                }
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

            if (!isTeacherAvailable(selectedClass, day, time, lecture.teacher)) {
                render([success: false, message: "Teacher is not available at this time"] as JSON)
                return
            }

            if (canAddLectureToSlot(timetable, day, time, lecture)) {
                def newLecture = [
                subject: lecture.subject,
                teacher: lecture.teacher,
                room: assignRoom(selectedClass, day, time, lecture.type),
                type: lecture.type,
                batch: lecture.batch,
                color: session.colorMap[lecture.subject] 
            ]
                
                timetable[day][time] << newLecture

                if (lecture.type == "Lab") {
                    // Add the lab to the next slot as well
                    def nextTimeIndex = TIME_SLOTS.indexOf(time) + 1
                    if (nextTimeIndex < TIME_SLOTS.size()) {
                        def nextTime = TIME_SLOTS[nextTimeIndex]
                        timetable[day][nextTime] = timetable[day][nextTime] ?: []
                        timetable[day][nextTime] << newLecture
                    }
                }

                // Decrement available lecture card count only for the specific batch
                lecture.count--
                session.timetable[selectedClass] = timetable
                render([success: true, message: "Lecture assigned successfully", lecture: newLecture, nextSlot: lecture.type == "Lab" ? TIME_SLOTS[TIME_SLOTS.indexOf(time) + 1] : null] as JSON)
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

            def lectureCards = session.lectureCards[selectedClass] ?: []
            if (lectureCards.isEmpty()) {
                render([success: false, message: "No lecture cards found for the selected class"] as JSON)
                return
            }

            def timetable = [:]
            WEEK_DAYS.each { day ->
                timetable[day] = [:]
                TIME_SLOTS.each { time ->
                    timetable[day][time] = []
                }
            }

            // Shuffle the lecture cards to randomize placement
            Collections.shuffle(lectureCards)

            lectureCards.each { card ->
                def remainingLectures = card.count
                while (remainingLectures > 0) {
                    def slot = findRandomAvailableSlot(timetable, card, selectedClass)
                    if (slot) {
                        def lecture = [
                            subject: card.subject,
                            teacher: card.teacher,
                            class: selectedClass,
                            room: assignRoom(selectedClass, slot.day, slot.time, card.type),
                            type: card.type,
                            batch: card.batch
                        ]

                        timetable[slot.day][slot.time] << lecture
                        if (card.type == "Lab") {
                            def nextTimeIndex = TIME_SLOTS.indexOf(slot.time) + 1
                            if (nextTimeIndex < TIME_SLOTS.size()) {
                                def nextTime = TIME_SLOTS[nextTimeIndex]
                                timetable[slot.day][nextTime] << lecture
                            }
                        }
                        remainingLectures--
                        card.count--
                    } else {
                        break
                    }
                }
            }

            def colorMap = assignColorsToSubjects()
            session.timetable[selectedClass] = timetable
            session.lectureCards[selectedClass] = lectureCards

            render([success: true, timetable: timetable, lectureCards: lectureCards, colorMap: colorMap] as JSON)
        } catch (Exception e) {
            log.error("Error generating timetable: ${e.message}", e)
            render([success: false, message: "Error generating timetable: ${e.message}"] as JSON)
        }
    }

    private Map findRandomAvailableSlot(Map timetable, Map card, String selectedClass) {
        def availableSlots = []
        WEEK_DAYS.each { day ->
            TIME_SLOTS.each { time ->
                if (canAddLectureToSlot(timetable, day, time, card) && 
                    isTeacherAvailable(selectedClass, day, time, card.teacher)) {
                    availableSlots << [day: day, time: time]
                }
            }
        }
        return availableSlots ? availableSlots[new Random().nextInt(availableSlots.size())] : null
    }

    private void assignSessions(List cards, Map timetable, String selectedClass) {
        cards.each { card ->
            def remainingLectures = card.count
            while (remainingLectures > 0) {
                def slot = findAvailableSlot(timetable, card, selectedClass)
                if (slot) {
                    def lecture = [
                        subject: card.subject,
                        teacher: card.teacher,
                        class: selectedClass,
                        room: assignRoom(selectedClass, slot.day, slot.time, card.type),
                        type: card.type,
                        batch: card.batch // This is important for labs and tutorials
                    ]

                    timetable[slot.day][slot.time] << lecture
                    if (card.type == "Lab") {
                        def nextTimeIndex = TIME_SLOTS.indexOf(slot.time) + 1
                        if (nextTimeIndex < TIME_SLOTS.size()) {
                            def nextTime = TIME_SLOTS[nextTimeIndex]
                            timetable[slot.day][nextTime] << lecture
                        }
                    }
                    remainingLectures--
                    card.count--
                } else {
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

    private boolean canAddLectureToSlot(Map timetable, String day, String time, Map newLecture) {
        def currentSlot = timetable[day]?[time] ?: []
        
        if (newLecture.type == "Lecture") {
            return currentSlot.isEmpty()
        } else if (newLecture.type == "Lab") {
            // For labs, check if the next slot is also available
            def nextTimeIndex = TIME_SLOTS.indexOf(time) + 1
            if (nextTimeIndex < TIME_SLOTS.size()) {
                def nextTime = TIME_SLOTS[nextTimeIndex]
                def nextSlot = timetable[day]?[nextTime] ?: []
                // Check if there's space for this batch in both current and next slot
                return canAddBatchToSlot(currentSlot, newLecture) && 
                    canAddBatchToSlot(nextSlot, newLecture) &&
                    !isTeacherOrRoomOccupied(currentSlot, newLecture) &&
                    !isTeacherOrRoomOccupied(nextSlot, newLecture)
            }
            return false
        } else if (newLecture.type == "Tutorial") {
            return canAddBatchToSlot(currentSlot, newLecture) &&
                !isTeacherOrRoomOccupied(currentSlot, newLecture)
        }

        return false
    }

    private boolean isTeacherOrRoomOccupied(List slotSessions, Map newLecture) {
        return slotSessions.any { session ->
            session.teacher == newLecture.teacher || session.room == newLecture.room
        }
    }

    private boolean isTeacherAvailable(String selectedClass, String day, String time, String teacher) {
        return !session.timetable.any { classGroup, classTimetable ->
            classTimetable[day]?[time]?.any { session ->
                session.teacher == teacher
            }
        }
    }

    private boolean canAddBatchToSlot(List slotSessions, Map newLecture) {
        // Check if this batch already exists in the slot for any subject
        if (slotSessions.any { it.batch == newLecture.batch }) {
            return false
        }
        
        // Check if there's space for another session (max 3 batches per slot)
        if (slotSessions.size() >= 3) {
            return false
        }
        
        // Check if there's already a lecture in this slot
        if (slotSessions.any { it.type == "Lecture" }) {
            return false
        }
        
        return true
    }

    private Map findAvailableSlot(Map timetable, Map card, String selectedClass) {
        def availableSlots = []
        WEEK_DAYS.each { day ->
            TIME_SLOTS.each { time ->
                if (canAddLectureToSlot(timetable, day, time, card) && 
                    isTeacherAvailable(selectedClass, day, time, card.teacher)) {
                    def currentSlotSize = timetable[day][time].size()
                    if (card.type != "Lecture" && currentSlotSize < 3) {
                        // Prioritize slots that already have sessions (but less than 3)
                        availableSlots.add(0, [day: day, time: time])
                    } else {
                        availableSlots << [day: day, time: time]
                    }
                }
            }
        }
        return availableSlots ? availableSlots[0] : null
    }

    private String assignRoom(String selectedClass, String day, String time, String type) {
        def occupiedRooms = session.timetable.values().collect { classTimetable ->
            classTimetable[day]?[time]?.collect { it.room }
        }.flatten().findAll()

        switch (type) {
            case "Lecture":
                def availableRooms = ROOMS - occupiedRooms
                return availableRooms ? availableRooms[new Random().nextInt(availableRooms.size())] : "TBD"
            
            case "Lab":
                def availableLabRooms = LABROOMS - occupiedRooms
                return availableLabRooms ? availableLabRooms[new Random().nextInt(availableLabRooms.size())] : "TBD"
            
            case "Tutorial":
                def availableRooms = (ROOMS + LABROOMS) - occupiedRooms
                return availableRooms ? availableRooms[new Random().nextInt(availableRooms.size())] : "TBD"
            
            default:
                return "TBD"
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
        headerRow.createCell(2).setCellValue("Teacher")
        headerRow.createCell(3).setCellValue("Class")
        headerRow.createCell(4).setCellValue("Lectures Per Week")

        // Create dropdowns
        XSSFDataValidationHelper dvHelper = new XSSFDataValidationHelper(sheet)

        // Subject dropdown
        addDropdownValidation(sheet, dvHelper, 0, SUBJECTS)

        // Type dropdown
        addDropdownValidation(sheet, dvHelper, 1, ["Lecture", "Lab", "Tutorial"])

        // Teacher dropdown
        addDropdownValidation(sheet, dvHelper, 2, TEACHERS)

        // Class dropdown
        addDropdownValidation(sheet, dvHelper, 3, CLASSES)

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
        XSSFDataValidationConstraint dvConstraint = (XSSFDataValidationConstraint) dvHelper.createExplicitListConstraint(items as String[])
        XSSFDataValidation validation = (XSSFDataValidation) dvHelper.createValidation(dvConstraint, addressList)
        sheet.addValidationData(validation)
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

                def subject = row.getCell(0)?.stringCellValue
                def type = row.getCell(1)?.stringCellValue
                def teacher = row.getCell(2)?.stringCellValue
                def classGroup = row.getCell(3)?.stringCellValue
                def lecturesPerWeek = row.getCell(4)?.numericCellValue?.intValue()

                if (subject && type && teacher && classGroup && lecturesPerWeek) {
                    def key = "${subject}_${classGroup}_${type}"
                    
                    def entry = [
                        subject: subject,
                        teacher: teacher,
                        class: classGroup,
                        lecturesPerWeek: lecturesPerWeek,
                        type: type
                    ]

                    if (type == "Lab" || type == "Tutorial") {
                        // For Labs and Tutorials, add batch information
                        entry.batchCount = 3  // Assuming 3 batches for Labs and Tutorials
                    }

                    session.subjectDetails[key] = entry
                }
            }

            workbook.close()
            flash.message = "Subject mapping uploaded successfully"
        } catch (Exception e) {
            log.error("Error processing Excel file: ${e.message}", e)
            flash.message = "Error processing Excel file: ${e.message}"
        }

        redirect(action: "index")
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
            case 'option3': // Lab
                timetable = fetchTimetableForLab(value)
                break
            case 'option4': // Room
                timetable = fetchTimetableForRoom(value)
                break
            default:
                render([success: false, message: "Invalid entity type"] as JSON)
                return
        }

        render([success: true, timetable: timetable, colorMap: session.colorMap] as JSON)
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

    private Map fetchTimetableForLab(String lab) {
        def timetable = [:]
        session.timetable.each { className, classTimetable ->
            classTimetable.each { day, daySlots ->
                daySlots.each { time, sessions ->
                    sessions.each { session ->
                        if (session.room == lab) {
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
                            sgsgdgdg
                        }
                    }
                }
            }
        }
        return timetable
    }
}    
