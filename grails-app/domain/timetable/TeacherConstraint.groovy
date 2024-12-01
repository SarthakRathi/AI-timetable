package timetable

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class TeacherConstraint {
    String teacher
    String workingDaysJson  // Store as JSON string
    String availableSlotsJson  // Store as JSON string
    
    static transients = ['workingDays', 'availableSlots']  // These are derived properties
    
    static constraints = {
        teacher nullable: false, blank: false, unique: true
        workingDaysJson nullable: false
        availableSlotsJson nullable: false
    }

    static mapping = {
        version false
        workingDaysJson type: 'text', column: 'working_days'
        availableSlotsJson type: 'text', column: 'available_slots'
    }
    
    // Getters and setters for workingDays
    List<String> getWorkingDays() {
        return workingDaysJson ? new JsonSlurper().parseText(workingDaysJson) as List<String> : []
    }
    
    void setWorkingDays(List<String> days) {
        this.workingDaysJson = JsonOutput.toJson(days ?: [])
    }
    
    // Getters and setters for availableSlots
    Map<String, List<String>> getAvailableSlots() {
        return availableSlotsJson ? new JsonSlurper().parseText(availableSlotsJson) as Map<String, List<String>> : [:]
    }
    
    void setAvailableSlots(Map<String, List<String>> slots) {
        this.availableSlotsJson = JsonOutput.toJson(slots ?: [:])
    }
    
    // Helper method for UI
    Map<String, Boolean> getWorkingDaysMap() {
        def allDays = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
        return allDays.collectEntries { day ->
            [(day): getWorkingDays().contains(day)]
        }
    }
    
    // Helper method for UI
    Map<String, Map<String, Boolean>> getAvailableSlotsMap() {
        def allTimeSlots = ['8:00', '09:00', '10:00', '11:00', '12:00', '1:00', '2:00', '3:00', '4:00', '5:00']
        def allDays = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
        
        Map<String, List<String>> slots = getAvailableSlots()
        return allDays.collectEntries { day ->
            [(day): allTimeSlots.collectEntries { slot ->
                [(slot): slots[day]?.contains(slot) ?: false]
            }]
        }
    }
    
    String toString() {
        return "TeacherConstraint(teacher: $teacher, workingDays: ${getWorkingDays()}, availableSlots: ${getAvailableSlots()})"
    }
}