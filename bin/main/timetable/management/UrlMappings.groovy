// Path : grails-app/controllers/timetable/management/UrlMappings.groovy

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
        "/timetable/downloadTimetable"(controller: "timetable", action: "downloadTimetable")
        "/timetable/deleteSubject"(controller: "timetable", action: "deleteSubject")
        "/timetable/getRemainingLectures"(controller: "timetable", action: "getRemainingLectures")
        "/"(controller: "timetable", action: "index")
    }
}
