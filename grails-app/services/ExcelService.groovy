// File: grails-app/services/timetable/ExcelService.groovy
package timetable

import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.*
import org.springframework.core.io.ResourceLoader
import org.springframework.beans.factory.annotation.Autowired
import groovy.util.logging.Log

@Log
class ExcelService {
    @Autowired
    ResourceLoader resourceLoader

    XSSFWorkbook generateTimetableExcel(Map timetable, List weekDays, List timeSlots) {
        log.info("Generating Excel for received timetable data: ${timetable}")
        
        // Load the template file
        def resource = resourceLoader.getResource("classpath:timetable_template.xlsx")
        XSSFWorkbook workbook = new XSSFWorkbook(resource.inputStream)
        XSSFSheet sheet = workbook.getSheetAt(0)
        
        // Create styles
        CellStyle headerStyle = createHeaderStyle(workbook)
        CellStyle detailsStyle = createDetailsStyle(workbook)
        
        Row timeHeaderRow = sheet.createRow(0)
        timeHeaderRow.createCell(0) // Leave the first cell empty for "Day/Time" label
        timeSlots.eachWithIndex { time, idx ->
            Cell cell = timeHeaderRow.createCell(idx * 2 + 1)
            cell.setCellValue(time) // Set the time slot value
            cell.setCellStyle(headerStyle) // Apply the header style
        }

        // Set up the first column for weekdays
        weekDays.eachWithIndex { day, idx ->
            Row dayRow = sheet.createRow(idx * 2 + 1) // Rows start from second row
            Cell cell = dayRow.createCell(0)
            cell.setCellValue(day) // Set the weekday
            cell.setCellStyle(headerStyle) // Apply the header style
        }

        // Fill in timetable data
        weekDays.eachWithIndex { day, dayIdx ->
            timeSlots.eachWithIndex { timeSlot, colIndex ->
                def cellData = timetable[day]?[timeSlot]
                if (cellData) {
                    Cell classCell = getOrCreateCell(sheet, dayIdx * 2 + 1, colIndex * 2 + 1)
                    Cell subjectCell = getOrCreateCell(sheet, dayIdx * 2 + 1, colIndex * 2 + 2)
                    Cell roomCell = getOrCreateCell(sheet, dayIdx * 2 + 2, colIndex * 2 + 1)
                    Cell teacherCell = getOrCreateCell(sheet, dayIdx * 2 + 2, colIndex * 2 + 2)

                    classCell.setCellValue(cellData.class ?: '')
                    classCell.setCellStyle(detailsStyle)
                    subjectCell.setCellValue(cellData.subject ?: '')
                    subjectCell.setCellStyle(detailsStyle)
                    roomCell.setCellValue(cellData.room ?: '')
                    roomCell.setCellStyle(detailsStyle)
                    teacherCell.setCellValue(cellData.teacher ?: '')
                    teacherCell.setCellStyle(detailsStyle)
                }
            }
        }
        
        return workbook
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle()
        Font font = workbook.createFont()
        font.setBold(true)
        font.setFontHeightInPoints((short) 12) // Cast the font size to short
        style.setFont(font)
        style.setAlignment(HorizontalAlignment.CENTER)
        style.setVerticalAlignment(VerticalAlignment.CENTER)
        return style
    }

    private CellStyle createDetailsStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle()
        style.setAlignment(HorizontalAlignment.CENTER)
        style.setVerticalAlignment(VerticalAlignment.CENTER)
        return style
    }

    private Cell getOrCreateCell(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex)
        if (row == null) {
            row = sheet.createRow(rowIndex)
        }
        Cell cell = row.getCell(colIndex)
        if (cell == null) {
            cell = row.createCell(colIndex)
        }
        return cell
    }
}
