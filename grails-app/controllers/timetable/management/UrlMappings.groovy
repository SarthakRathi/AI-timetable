package timetable

class UrlMappings {
    static mappings = {
        "/$controller/$action?/$id?(.$format)?" {
            constraints {
                // Apply constraints here
            }
        }

        "500"(view:'/error')
        "404"(view:'/notFound')

        // Timetable-specific mappings
        "/timetable/index"(controller: 'timetable', action: 'index')
        "/timetable/addSubject"(controller: "timetable", action: "addSubject")
        "/timetable/deleteSubject"(controller: "timetable", action: "deleteSubject")
        "/timetable/deleteMultipleSubjects"(controller: "timetable", action: "deleteMultipleSubjects")
        "/timetable/getRemainingLectures"(controller: "timetable", action: "getRemainingLectures")
        "/timetable/downloadTemplate"(controller: "timetable", action: "downloadTemplate")
        "/timetable/uploadSubjectMapping"(controller: "timetable", action: "uploadSubjectMapping")
        "/timetable/assignLecture"(controller: "timetable", action: "assignLecture")
        "/timetable/generateTimetable"(controller: "timetable", action: "generateTimetable")
        "/timetable/resetTimetable"(controller: "timetable", action: "resetTimetable")
        "/timetable/saveTeacherConstraints"(controller: "timetable", action: "saveTeacherConstraints")
        "/timetable/getTeacherConstraints"(controller: "timetable", action: "getTeacherConstraints")

        // Set the timetable index as the main page
        "/"(controller: "timetable", action: "index")
    }
}