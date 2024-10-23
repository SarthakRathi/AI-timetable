// File: grails-app/controllers/timetable/TimetableController.groovy
package timetable

import grails.converters.JSON
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.ss.util.CellRangeAddressList
import org.apache.poi.xssf.usermodel.XSSFDataValidation
import org.apache.poi.xssf.usermodel.XSSFDataValidationConstraint
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper
import org.apache.poi.xssf.usermodel.XSSFName

class TimetableController {
    ExcelService excelService

    // Predefined lists
    private static final List<String> SUBJECTS = ['Data Structures & Algorithms', 'Database Management System', 'Discrete Mathematics', 'Probability & Statistics', 'Design Thinking', 'Universal Human Value', 'Data Ethics', 'Artificial Intelligence', 'Deep Learning', 'Big Data Analytics', 'Data Communication & Networking', 'Research Methodology And IPR', 'Project-I', 'Professional Elective']
    private static final List<String> CLASSES = ['SY A', 'SY B', 'TY A']
    private static final List<String> CLASSROOMS = ['101', '102', '103', '104', '105']
    private static final List<String> LABROOMS = ['Lab1', 'Lab2', 'Lab3']
    private static final List<String> TUTORIALROOMS = ['Tutorial1', 'Tutorial2', 'Tutorial3']
    private static final List<String> TEACHERS = ['Nilesh Sable', 'Chandrakant Banchhor', 'Anuradha Yenkikar', 'Pradnya Mehta', 'Amol Patil', 'Pranjal Pandit', 'Vidya Gaikwad']
    private static final List<String> WEEK_DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday']
    private static final List<String> TIME_SLOTS = ['8:00', '09:00', '10:00', '11:00', '12:00', '1:00', '2:00', '3:00', '4:00', '5:00']
    
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
            classrooms: CLASSROOMS.collect { it.encodeAsJSON() },
            tutorialRooms: TUTORIALROOMS.collect { it.encodeAsJSON() },
            allRooms: (CLASSROOMS + LABROOMS + TUTORIALROOMS).collect { it.encodeAsJSON() },
            colorMap: colorMap  
        ]
    }

    private static final Map<String, String> SESSION_COLORS = [
        'Lecture': '#ACDDDE',
        'Lab': '#FEF8DD',
        'Tutorial': '#DCEDC1'
    ]

    private Map<String, String> assignColorsToSubjects() {
        if (!session.colorMap) {
            session.colorMap = [:]
        }
        
        if (session.subjectDetails) {
            session.subjectDetails.each { key, details ->
                session.colorMap[details.subject] = SESSION_COLORS[details.type]
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
                    assignedLectures += slotList.count { it.subject == details.subject && it.batch == details.batch }
                }
            }
            def remainingLectures = details.lecturesPerWeek - assignedLectures
            
            if (remainingLectures > 0) {
                def cardBase = [
                    subject: details.subject,
                    teacher: details.teacher,
                    count: remainingLectures,
                    type: details.type,
                    roomAllocation: details.roomAllocation,
                    manualRoom: details.manualRoom
                ]
                
                if (details.type == "Lecture") {
                    session.lectureCards[selectedClass] << cardBase + [
                        id: "${details.subject}_${details.class}_${details.type}".toString()
                    ]
                } else {
                    // For Labs and Tutorials, create separate cards for each batch
                    def batches = details.batch ? [details.batch] : ['1', '2', '3']
                    batches.each { batchNumber ->
                        session.lectureCards[selectedClass] << cardBase + [
                            id: "${details.subject}_${details.class}_${details.type}_Batch${batchNumber}".toString(),
                            batch: batchNumber
                        ]
                    }
                }
            }
        }
    }

    def resetTimetable() {
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
        
        flash.message = "Timetable reset successfully"

        // Check if it's an AJAX request
        if (request.xhr) {
            render([success: true, message: flash.message] as JSON)
        } else {
            redirect(action: "index")
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

            if (!isTeacherAvailable(selectedClass, day, time, lecture.teacher)) {
                render([success: false, message: "Teacher is not available at this time"] as JSON)
                return
            }

            if (canAddLectureToSlot(timetable, day, time, lecture)) {
                def subjectKey = "${lecture.subject}_${selectedClass}_${lecture.type}_${lecture.batch ?: 'NoBatch'}"
                def lectureDetails = session.subjectDetails[subjectKey]
                
                def newLecture = [
                    subject: lecture.subject,
                    teacher: lecture.teacher,
                    room: assignRoom(selectedClass, day, time, lecture.type, lectureDetails),
                    type: lecture.type,
                    batch: lecture.batch,
                    color: SESSION_COLORS[lecture.type]
                ]
                
                timetable[day][time] << newLecture

                if (lecture.type == "Lab") {
                    def nextTimeIndex = TIME_SLOTS.indexOf(time) + 1
                    if (nextTimeIndex < TIME_SLOTS.size()) {
                        def nextTime = TIME_SLOTS[nextTimeIndex]
                        timetable[day][nextTime] = timetable[day][nextTime] ?: []
                        timetable[day][nextTime] << newLecture
                    }
                }

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
                            room: assignRoom(selectedClass, slot.day, slot.time, card.type, session.subjectDetails["${card.subject}_${selectedClass}_${card.type}_${card.batch ?: 'NoBatch'}"]),
                            type: card.type,
                            batch: card.batch,
                            color: SESSION_COLORS[card.type]
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
        def occupiedRooms = session.timetable.values().collect { classTimetable ->
            classTimetable[day]?[time]?.collect { it.room }
        }.flatten().findAll()

        switch (type) {
            case "Lecture":
                def availableRooms = CLASSROOMS - occupiedRooms
                return availableRooms ? availableRooms[new Random().nextInt(availableRooms.size())] : "TBD"
            
            case "Lab":
                def availableLabRooms = LABROOMS - occupiedRooms
                return availableLabRooms ? availableLabRooms[new Random().nextInt(availableLabRooms.size())] : "TBD"
            
            case "Tutorial":
                def availableTutorialRooms = TUTORIALROOMS - occupiedRooms
                return availableTutorialRooms ? availableTutorialRooms[new Random().nextInt(availableTutorialRooms.size())] : "TBD"
            
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

    private void resetTimetableWithoutRedirect() {
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
                        }
                    }
                }
            }
        }
        return timetable
    }
}    
