package timetable

class UrlMappings {
    static mappings = {
        "/$controller/$action?/$id?(.$format)?" {
            constraints {
                // Apply constraints here
            }
        }

        // This should point to a general index GSP or be removed if not used
        "/"(view:"/index")

        "500"(view:'/error')
        "404"(view:'/notFound')

        // Mapping to your TimetableController
        "/timetable/index"(controller: 'timetable', action: 'index')
        "/timetable/addSubject"(controller: "timetable", action: "addSubject")
        "/timetable/downloadTimetable"(controller: "timetable", action: "downloadTimetable")
        "/timetable/deleteSubject"(controller: "timetable", action: "deleteSubject")
        "/timetable/getRemainingLectures"(controller: "timetable", action: "getRemainingLectures")
        "/timetable/downloadTemplate"(controller: "timetable", action: "downloadTemplate")
        "/timetable/uploadSubjectMapping"(controller: "timetable", action: "uploadSubjectMapping")
        "/timetable/setTimetableHours"(controller: "timetable", action: "setTimetableHours")
        "/"(controller: "timetable", action: "index")
    }
}