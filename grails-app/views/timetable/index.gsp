<!-- File: grails-app/views/timetable/index.gsp -->

<%@ page import="grails.converters.JSON" %>

<!DOCTYPE html>
<html>
<head>
    <title>Timetable Generator</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
    <style>
        .lecture-card {
            border: 1px solid #ddd;
            padding: 5px;
            cursor: pointer;
            width: fit-content;
        }
        .lecture-card.selected {
            background-color: #e9ecef;
        }
        .lecture-card.disabled {
            opacity: 0.5;
            background-color: #f8f9fa;
            cursor: not-allowed;
        }
        .timetable-cell {
            font-size: 12px; /* Smaller text size */
        }
        .step-content {
            display: none;
        }
        .step-content.active {
            display: block;
        }
        .stepper-item {
            flex: 1;
            text-align: center;
            padding: 10px;
            background-color: #f8f9fa;
            border: 1px solid #dee2e6;
            cursor: pointer;
        }
        .stepper-item.active {
            background-color: #007bff;
            color: white;
        }
        .timetable-cell.invalid-drop {
            background-color: #ffebee !important;
            transition: background-color 0.3s;
        }

        .timetable-cell.valid-drop {
            background-color: #e8f5e9 !important;
            transition: background-color 0.3s;
        }
        .accordion-button:not(.collapsed) {
            background-color: #e7f1ff;
            color: #0c63e4;
        }

        .accordion-button:focus {
            box-shadow: none;
            border-color: rgba(0,0,0,.125);
        }

        .accordion-item {
            margin-bottom: 1rem;
        }
    </style>
</head>
<body>
    <div class="container mt-3">
        <h1 class="text-center mb-4">Timetable Generator</h1>

        <!-- Horizontal Stepper -->
        <div class="d-flex justify-content-between mb-4">
            <div class="stepper-item active" data-step="1">Step 1: Subject Mapping</div>
            <div class="stepper-item" data-step="2">Step 2: Constraints</div>
            <div class="stepper-item" data-step="3">Step 3: Timetable Generation</div>
            <div class="stepper-item" data-step="4">Step 4: Timetable Summary</div>
        </div>

        <!-- Step 1: Subject Mapping -->
        <div class="step-content active" id="step1">
            <!-- Form for adding subject details -->
            <form action="${createLink(controller: 'timetable', action: 'addSubject')}" method="POST" class="shadow-sm p-3 bg-white rounded mb-4">
                <div class="mb-3">
                    <label for="subject" class="form-label">Subject:</label>
                    <select id="subject" name="subject" class="form-select" required>
                        <option value="">Select a subject</option>
                        <g:each in="${subjects}" var="subject">
                            <option value="${subject}">${subject}</option>
                        </g:each>
                    </select>
                </div>
                <div class="mb-3">
                    <label for="type" class="form-label">Type:</label>
                    <select id="type" name="type" class="form-select" required>
                        <option value="Lecture">Lecture</option>
                        <option value="Lab">Lab</option>
                        <option value="Tutorial">Tutorial</option>
                    </select>
                </div>
                <div class="mb-3">
                    <label for="class" class="form-label">Class:</label>
                    <select id="class" name="class" class="form-select" required>
                        <option value="">Select a class</option>
                        <g:each in="${classes}" var="classOption">
                            <option value="${classOption}">${classOption}</option>
                        </g:each>
                    </select>
                </div>
                <div class="mb-3 batch-field" style="display: none;">
                    <label for="batch" class="form-label">Batch:</label>
                    <select id="batch" name="batch" class="form-select">
                        <option value="">Select a batch</option>
                        <option value="1">Batch 1</option>
                        <option value="2">Batch 2</option>
                        <option value="3">Batch 3</option>
                    </select>
                </div>
                <div class="mb-3">
                    <label for="teacher" class="form-label">Teacher:</label>
                    <select id="teacher" name="teacher" class="form-select" required>
                        <option value="">Select a teacher</option>
                        <g:each in="${teachers}" var="teacher">
                            <option value="${teacher}">${teacher}</option>
                        </g:each>
                    </select>
                </div>
                <div class="mb-3">
                    <label for="roomAllocation" class="form-label">Room Allocation:</label>
                    <select id="roomAllocation" name="roomAllocation" class="form-select" required>
                        <option value="automatic">Automatic</option>
                        <option value="manual">Manual</option>
                    </select>
                </div>
                <div class="mb-3" id="manualRoomSelection" style="display: none;">
                    <label for="manualRoom" class="form-label">Select Room:</label>
                    <select id="manualRoom" name="manualRoom" class="form-select">
                        <option value="">Select a room</option>
                        <g:each in="${allRooms}" var="room">
                            <option value="${room}">${room}</option>
                        </g:each>
                    </select>
                </div>
                <div class="mb-3">
                    <label for="lecturesPerWeek" class="form-label">Number of Lectures per Week:</label>
                    <input type="number" id="lecturesPerWeek" name="lecturesPerWeek" class="form-control" required min="1">
                </div>
                <button type="submit" class="btn btn-primary">Add Subject</button>
            </form>

            <div class="shadow-sm p-3 bg-white rounded mb-4">
                <!-- Download template button -->
                <a href="${createLink(controller: 'timetable', action: 'downloadTemplate')}" class="btn btn-secondary mb-3">
                    Download Excel Template
                </a>

                <!-- Upload filled template form -->
                <form action="${createLink(controller: 'timetable', action: 'uploadSubjectMapping')}" method="POST" enctype="multipart/form-data">
                    <div class="mb-3">
                        <input type="file" class="form-control" id="excelFile" name="excelFile" accept=".xlsx" required>
                    </div>
                    <button type="submit" class="btn btn-primary">Upload and Process</button>
                </form>
            </div>

            <!-- Table to display subject details -->
            <h2>Subject Details</h2>

            <div class="mb-3">
                <button id="deleteSelectedBtn" class="btn btn-danger" style="display: none;">
                    <i class="bi bi-trash3-fill"></i> Delete Selected
                </button>
            </div>

            <table class="table table-striped table-bordered table-hover shadow-sm p-3 mb-3 bg-white rounded">
                <thead>
                    <tr>
                        <th style="width: 40px">
                            <div class="d-flex justify-content-center">
                                <input type="checkbox" id="selectAll" class="form-check-input">
                            </div>
                        </th>
                        <th>Subject</th>
                        <th>Type</th>
                        <th>Teacher</th>
                        <th>Class</th>
                        <th>Batch</th>
                        <th>Room Allocation</th>
                        <th>Lectures per Week</th>
                        <th style="width: 50px">Delete</th>
                    </tr>
                </thead>
                <tbody>
                    <g:if test="${subjectDetails}">
                        <g:each in="${subjectDetails}" var="entry">
                            <tr>
                                <td class="text-center">
                                    <input type="checkbox" class="form-check-input subject-checkbox" data-key="${entry.key}">
                                </td>
                                <td>${entry.value.subject}</td>
                                <td>${entry.value.type}</td>
                                <td>${entry.value.teacher}</td>
                                <td>${entry.value.class}</td>
                                <td>
                                    ${entry.value.batch != null ? 
                                        (entry.value.batch instanceof Number ? 
                                            entry.value.batch.intValue() : 
                                            (entry.value.batch.toString().isNumber() ? 
                                                entry.value.batch.toString().toFloat().intValue() : 
                                                entry.value.batch)
                                        ) : 'N/A'}
                                </td>
                                <td>${entry.value.roomAllocation == 'automatic' ? 'Automatic' : entry.value.manualRoom}</td>
                                <td>${entry.value.lecturesPerWeek}</td>
                                <td class="text-center">
                                    <i class="bi bi-trash3-fill" onClick="deleteSubject('${entry.key}')"></i>
                                </td>
                            </tr>
                        </g:each>
                    </g:if>
                    <g:else>
                        <tr>
                            <td colspan="9" class="text-center">No subject mappings found</td>
                        </tr>
                    </g:else>
                </tbody>
            </table>
        </div>

        <!-- Step 2: Constraints -->
        <div class="step-content" id="step2">
            <div class="accordion" id="constraintsAccordion">
                <!-- Teacher Constraints Accordion -->
                <div class="accordion-item">
                    <h2 class="accordion-header">
                        <button class="accordion-button" type="button" data-bs-toggle="collapse" 
                                data-bs-target="#teacherConstraints" aria-expanded="true" 
                                aria-controls="teacherConstraints">
                            Teacher Constraints
                        </button>
                    </h2>
                    <div id="teacherConstraints" class="accordion-collapse collapse show" 
                        data-bs-parent="#constraintsAccordion">
                        <div class="accordion-body">
                            <div class="mb-4">
                                <label for="constraintTeacher" class="form-label">Select Teacher:</label>
                                <select id="constraintTeacher" class="form-select">
                                    <g:each in="${teachers}" var="teacher">
                                        <option value="${teacher}">${teacher}</option>
                                    </g:each>
                                </select>
                            </div>

                            <!-- Time Slots Selection for Teachers -->
                            <div class="mb-4">
                                <h4>Available Time Slots</h4>
                                <div class="table-responsive">
                                    <div class="mb-3">
                                        <button type="button" class="btn btn-danger" id="clearAllTeacherSlots">Clear All</button>
                                        <button type="button" class="btn btn-success ms-2" id="selectAllTeacherSlots">Select All</button>
                                    </div>
                                    <table class="table table-bordered">
                                        <thead>
                                            <tr>
                                                <th>Time Slot</th>
                                                <g:each in="${allDays}" var="day">
                                                    <th>${day}</th>
                                                </g:each>
                                                <th>Select All</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <g:each in="${timeSlots}" var="slot">
                                                <tr>
                                                    <td>${slot}</td>
                                                    <g:each in="${allDays}" var="day">
                                                        <td>
                                                            <div class="form-check d-flex justify-content-center">
                                                                <input class="form-check-input teacher-time-slot" 
                                                                    type="checkbox" 
                                                                    data-day="${day}" 
                                                                    data-slot="${slot}"
                                                                    checked>
                                                            </div>
                                                        </td>
                                                    </g:each>
                                                    <td>
                                                        <div class="form-check d-flex justify-content-center">
                                                            <input class="form-check-input teacher-select-row" 
                                                                type="checkbox" 
                                                                data-slot="${slot}"
                                                                checked>
                                                        </div>
                                                    </td>
                                                </tr>
                                            </g:each>
                                        </tbody>
                                        <tfoot>
                                            <tr>
                                                <td>Select All</td>
                                                <g:each in="${allDays}" var="day">
                                                    <td>
                                                        <div class="form-check d-flex justify-content-center">
                                                            <input class="form-check-input teacher-select-column" 
                                                                type="checkbox" 
                                                                data-day="${day}"
                                                                checked>
                                                        </div>
                                                    </td>
                                                </g:each>
                                                <td></td>
                                            </tr>
                                        </tfoot>
                                    </table>
                                </div>
                            </div>

                            <div class="text-center">
                                <button id="saveTeacherConstraints" class="btn btn-primary">Save Teacher Constraints</button>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Class Constraints Accordion -->
                <div class="accordion-item">
                    <h2 class="accordion-header">
                        <button class="accordion-button collapsed" type="button" data-bs-toggle="collapse" 
                                data-bs-target="#classConstraints" aria-expanded="false" 
                                aria-controls="classConstraints">
                            Class Constraints
                        </button>
                    </h2>
                    <div id="classConstraints" class="accordion-collapse collapse" 
                        data-bs-parent="#constraintsAccordion">
                        <div class="accordion-body">
                            <div class="mb-4">
                                <label for="constraintClass" class="form-label">Select Class:</label>
                                <select id="constraintClass" class="form-select">
                                    <g:each in="${classes}" var="classOption">
                                        <option value="${classOption}">${classOption}</option>
                                    </g:each>
                                </select>
                            </div>

                            <!-- Time Slots Selection for Classes -->
                            <div class="mb-4">
                                <h4>Available Time Slots</h4>
                                <div class="table-responsive">
                                    <div class="mb-3">
                                        <button type="button" class="btn btn-danger" id="clearAllClassSlots">Clear All</button>
                                        <button type="button" class="btn btn-success ms-2" id="selectAllClassSlots">Select All</button>
                                    </div>
                                    <table class="table table-bordered">
                                        <thead>
                                            <tr>
                                                <th>Time Slot</th>
                                                <g:each in="${allDays}" var="day">
                                                    <th>${day}</th>
                                                </g:each>
                                                <th>Select All</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <g:each in="${timeSlots}" var="slot">
                                                <tr>
                                                    <td>${slot}</td>
                                                    <g:each in="${allDays}" var="day">
                                                        <td>
                                                            <div class="form-check d-flex justify-content-center">
                                                                <input class="form-check-input class-time-slot" 
                                                                    type="checkbox" 
                                                                    data-day="${day}" 
                                                                    data-slot="${slot}"
                                                                    checked>
                                                            </div>
                                                        </td>
                                                    </g:each>
                                                    <td>
                                                        <div class="form-check d-flex justify-content-center">
                                                            <input class="form-check-input class-select-row" 
                                                                type="checkbox" 
                                                                data-slot="${slot}"
                                                                checked>
                                                        </div>
                                                    </td>
                                                </tr>
                                            </g:each>
                                        </tbody>
                                        <tfoot>
                                            <tr>
                                                <td>Select All</td>
                                                <g:each in="${allDays}" var="day">
                                                    <td>
                                                        <div class="form-check d-flex justify-content-center">
                                                            <input class="form-check-input class-select-column" 
                                                                type="checkbox" 
                                                                data-day="${day}"
                                                                checked>
                                                        </div>
                                                    </td>
                                                </g:each>
                                                <td></td>
                                            </tr>
                                        </tfoot>
                                    </table>
                                </div>
                            </div>

                            <div class="text-center">
                                <button id="saveClassConstraints" class="btn btn-primary">Save Class Constraints</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- Step 3: Timetable Generation -->
        <div class="step-content" id="step3">
            <!-- Class filter dropdown -->
            <form id="filterForm" action="${createLink(controller: 'timetable', action: 'index')}" method="GET" class="mb-3">
                <div class="row align-items-center">
                    <div class="col-auto">
                        <label for="selectedClass" class="col-form-label">Filter by Class:</label>
                    </div>
                    <div class="col-auto">
                        <select id="selectedClass" name="selectedClass" class="form-select">
                            <g:each in="${classes}" var="classOption">
                                <option value="${classOption}" ${classOption == selectedClass ? 'selected' : ''}>${classOption}</option>
                            </g:each>
                        </select>
                    </div>
                </div>
                <input type="hidden" name="currentStep" id="currentStepInput" value="2">
            </form>

            <!-- Lecture Cards -->
            <h2>Lecture Cards for ${selectedClass}</h2>
            <div id="lectureCards" class="row mb-3">
                <g:each in="${lectureCards}" var="card">
                    <div class="col-md-3 mb-2">
                        <div class="lecture-card shadow p-3 rounded" data-id="${card.id}">
                            <h6>${card.subject} (${card.type})</h6>
                            <h6>Teacher: ${card.teacher}</h6>
                            <g:if test="${card.type != 'Lecture'}">
                                <h6>Batch: ${card.batch}</h6>
                            </g:if>
                            <h6>Room: ${card.roomAllocation == 'automatic' ? 'Automatic' : card.manualRoom}</h6>
                            <h6>Remaining: <span class="lecture-count">${card.count}</span></h6>
                        </div>
                    </div>
                </g:each>
            </div>                                                                                                                                                                                   

            <!-- Timetable display -->
            <h2>Current Timetable for ${selectedClass}</h2>
            <table id="timetable" class="table table-striped table-bordered shadow-sm p-3 mb-3 bg-white rounded">
                <thead>
                    <tr>
                        <th>Day / Time</th>
                        <g:each in="${timeSlots}" var="timeSlot">
                            <th>${timeSlot}</th>
                        </g:each>
                    </tr>
                </thead>
                <tbody>
                    <g:each in="${weekDays}" var="day">
                        <tr>
                            <th>${day}</th>
                            <g:each in="${timeSlots}" var="time" status="i">
                                <td class="timetable-cell" data-day="${day}" data-time="${time}">
                                    <g:if test="${timetable[day]?.get(time)?.size() > 0}">
                                        <g:each in="${timetable[day][time]}" var="session" status="j">
                                            <g:if test="${session}">
                                                <g:if test="${session.type != 'Lecture'}">
                                                    Batch ${session.batch}<br>
                                                </g:if>
                                                Subject: ${session.subject ?: 'N/A'}<br>
                                                Teacher: ${session.teacher ?: 'N/A'}<br>
                                                Room: ${session.room ?: 'TBD'}<br>
                                                Type: ${session.type ?: 'N/A'}<br>
                                                <g:if test="${session.type == 'Lab'}">
                                                    (2 hours)
                                                </g:if>
                                                <br>
                                            </g:if>
                                        </g:each>
                                    </g:if>
                                    <g:else>
                                        -
                                    </g:else>
                                </td>
                            </g:each>
                        </tr>
                    </g:each>
                </tbody>
            </table>

            <!-- Timetable Actions -->
            <div class="d-flex justify-content-center mt-4">
                <form action="${createLink(controller: 'timetable', action: 'generateTimetable')}" method="POST" class="me-2">
                    <button type="submit" id="generateTimetableBtn" class="btn btn-success">Generate Timetable</button>
                </form>
                <form action="${createLink(controller: 'timetable', action: 'resetTimetable')}" method="POST" class="me-2">
                    <input type="hidden" name="selectedClass" value="${selectedClass}">
                    <button type="submit" class="btn btn-warning">Reset Timetable</button>
                </form>
                <form action="${createLink(controller: 'timetable', action: 'downloadTimetable')}" method="POST">
                    <button type="submit" class="btn btn-primary">Download Timetable</button>
                </form>
            </div>
        </div>

        <!-- Step 4: Timetable Summary -->
        <div id="step4" class="step-content">
            <h3>Manage Timetables</h3>
            <div class="row mb-4">
                <!-- First Dropdown -->
                <div class="col-md-4">
                    <select id="dropdown1" class="form-control">
                        <option value="option1">Class</option>
                        <option value="option2">Teacher</option>
                        <option value="option3">Room</option>
                    </select>
                </div>

                <!-- Second Dropdown -->
                <div class="col-md-4">
                    <select id="dropdown2" class="form-control">
                        <option value="">Please select an option</option>
                    </select>
                </div>

                <!-- Button -->
                <div class="col-md-4">
                    <button id="displayButton" class="btn btn-primary w-100">Display</button>
                </div>
            </div>

            <!-- Timetable display for Step 3 -->
            <div id="step3TimetableContainer" style="display: none;">
                <h4 id="step3TimetableTitle"></h4>
                <table id="step3Timetable" class="table table-bordered table-hover table-striped shadow-sm p-3 mb-3 bg-white rounded">
                    <thead>
                        <tr>
                            <th>Day / Time</th>
                            <g:each in="${timeSlots}" var="timeSlot">
                                <th>${timeSlot}</th>
                            </g:each>
                        </tr>
                    </thead>
                    <tbody>
                    </tbody>
                </table>
                <div class="mt-3">
                    <button id="downloadEntityTimetable" class="btn btn-primary" style="display: none;">
                        <i class="bi bi-download"></i> Download Current View
                    </button>
                </div>
            </div>
        </div>

        <!-- Navigation Buttons -->
        <div class="d-flex justify-content-between mt-4">
            <button id="prevBtn" class="btn btn-secondary">Previous</button>
            <button id="nextBtn" class="btn btn-primary">Next</button>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script>

        var data = {
            classes: ${raw((classes as JSON).toString())},
            teachers: ${raw((teachers as JSON).toString())},
            labs: ${raw((labs as JSON).toString())},
            classrooms: ${raw((classrooms as JSON).toString())},
            tutorialRooms: ${raw((tutorialRooms as JSON).toString())},
            allRooms: ${raw((allRooms as JSON).toString())},
            weekDays: ${raw((weekDays as JSON).toString())},
            timeSlots: ${raw((timeSlots as JSON).toString())}
        };

        function deleteSubject(key) {
            $.ajax({
                url: '${createLink(controller: 'timetable', action: 'deleteSubject')}',
                method: 'POST',
                data: { key: key },
                success: function(response) {
                    if (response.success) {
                        location.reload(); // Reload the page to reflect changes
                    } else {
                        alert('Error: ' + response.message);
                    }
                },
                error: function() {
                    alert('Error deleting subject');
                }
            });
        }

        $(document).ready(function() {
            let currentStep = ${params.currentStep ? params.int('currentStep') : 1};
            const totalSteps = 4;

            function updateStepperDisplay() {
                $('.stepper-item').removeClass('active');
                $('.stepper-item[data-step="' + currentStep + '"]').addClass('active');
                $('.step-content').removeClass('active').hide();
                $('#step' + currentStep).fadeIn().addClass('active');

                // Update navigation buttons
                if (currentStep === 1) {
                    $('#prevBtn').hide();
                    $('#nextBtn').show();
                } else if (currentStep === totalSteps) {
                    $('#prevBtn').show();
                    $('#nextBtn').hide();
                } else {
                    $('#prevBtn').show();
                    $('#nextBtn').show();
                }

                // Update the hidden input with the current step
                $('#currentStepInput').val(currentStep);
            }

            // Initialize the correct step
            updateStepperDisplay();

            // Stepper functionality
            $('.stepper-item').click(function() {
                currentStep = parseInt($(this).data('step'));
                updateStepperDisplay();
            });

            // Navigation button functionality
            $('#nextBtn').click(function() {
                if (currentStep < totalSteps) {
                    currentStep++;
                    updateStepperDisplay();
                }
            });

            $('#prevBtn').click(function() {
                if (currentStep > 1) {
                    currentStep--;
                    updateStepperDisplay();
                }
            });

            if ($('#constraintTeacher option').length > 0) {
                $('#constraintTeacher').prop('selectedIndex', 0).trigger('change');
            }

            $('#clearAllSlots').click(function() {
                $('.time-slot, .select-row, .select-column').prop('checked', false);
            });

            // Select all slots
            $('#selectAllSlots').click(function() {
                $('.time-slot, .select-row, .select-column').prop('checked', true);
            });

            // Handle row selection (time slot)
            $('.select-row').change(function() {
                const slot = $(this).data('slot');
                const isChecked = $(this).is(':checked');
                $('.time-slot[data-slot="' + slot + '"]').prop('checked', isChecked);
                updateColumnCheckboxes();
            });

            // Handle column selection (day)
            $('.select-column').change(function() {
                const day = $(this).data('day');
                const isChecked = $(this).is(':checked');
                $('.time-slot[data-day="' + day + '"]').prop('checked', isChecked);
                updateRowCheckboxes();
            });

            // Update row checkboxes based on time slots
            function updateRowCheckboxes() {
                $('.select-row').each(function() {
                    const slot = $(this).data('slot');
                    const allChecked = $('.time-slot[data-slot="' + slot + '"]').length === 
                                    $('.time-slot[data-slot="' + slot + '"]:checked').length;
                    $(this).prop('checked', allChecked);
                });
            }

            // Update column checkboxes based on time slots
            function updateColumnCheckboxes() {
                $('.select-column').each(function() {
                    const day = $(this).data('day');
                    const allChecked = $('.time-slot[data-day="' + day + '"]').length === 
                                    $('.time-slot[data-day="' + day + '"]:checked').length;
                    $(this).prop('checked', allChecked);
                });
            }

            // Update checkboxes when individual time slots are clicked
            $('.time-slot').change(function() {
                updateRowCheckboxes();
                updateColumnCheckboxes();
            });

            $('#constraintTeacher').change(function() {
                const teacher = $(this).val();
                if (teacher) {
                    loadTeacherConstraints(teacher);
                } else {
                    resetConstraintForm();
                }
            });
            
            function loadTeacherConstraints(teacher) {
                $.ajax({
                    url: '${createLink(controller: "timetable", action: "getTeacherConstraints")}',
                    data: { teacher: teacher },
                    success: function(response) {
                        if (response.success) {
                            console.log("Loading constraints for", teacher, response); // Debug log
                            // Set time slots
                            $('.time-slot').each(function() {
                                const day = $(this).data('day');
                                const slot = $(this).data('slot');
                                const isChecked = response.availableSlots[day]?.includes(slot) || false;
                                $(this).prop('checked', isChecked);
                            });
                            
                            // Update row and column selectors
                            updateRowCheckboxes();
                            updateColumnCheckboxes();
                        } else {
                            console.error("Error loading constraints:", response.message);
                            alert('Error loading constraints: ' + response.message);
                        }
                    },
                    error: function(xhr, status, error) {
                        console.error("AJAX error:", error);
                        alert('Error loading constraints: ' + error);
                    }
                });
            }
            
            $('#saveConstraints').click(function() {
                const teacher = $('#constraintTeacher').val();
                if (!teacher) {
                    alert('Please select a teacher');
                    return;
                }
                
                // Collect available slots
                const availableSlots = {};
                const workingDays = new Set();
                
                data.weekDays.forEach(day => {
                    availableSlots[day] = [];
                    
                    // Get all checked slots for this day
                    $('.time-slot[data-day="' + day + '"]:checked').each(function() {
                        const slot = $(this).data('slot');
                        availableSlots[day].push(slot);
                        if (slot) {
                            workingDays.add(day);
                        }
                    });
                });
                
                console.log("Saving constraints for", teacher, {
                    workingDays: Array.from(workingDays),
                    availableSlots: availableSlots
                }); // Debug log
                
                // Save constraints
                $.ajax({
                    url: '${createLink(controller: "timetable", action: "saveTeacherConstraints")}',
                    method: 'POST',
                    contentType: 'application/json',
                    data: JSON.stringify({
                        teacher: teacher,
                        workingDays: Array.from(workingDays),
                        availableSlots: availableSlots
                    }),
                    success: function(response) {
                        if (response.success) {
                            alert('Constraints saved successfully');
                        } else {
                            console.error("Error saving constraints:", response.message);
                            alert('Error saving constraints: ' + response.message);
                        }
                    },
                    error: function(xhr, status, error) {
                        console.error("AJAX error:", error);
                        alert('Error saving constraints: ' + error);
                    }
                });
            });
            
            function resetConstraintForm() {
                $('.working-day, .time-slot').prop('checked', false);
            }

            $('#clearAllClassSlots').click(function() {
                $('.class-time-slot, .class-select-row, .class-select-column').prop('checked', false);
            });

            // Select all class slots
            $('#selectAllClassSlots').click(function() {
                $('.class-time-slot, .class-select-row, .class-select-column').prop('checked', true);
            });

            // Handle row selection for class
            $('.class-select-row').change(function() {
                const slot = $(this).data('slot');
                const isChecked = $(this).is(':checked');
                $('.class-time-slot[data-slot="' + slot + '"]').prop('checked', isChecked);
                updateClassColumnCheckboxes();
            });

            // Handle column selection for class
            $('.class-select-column').change(function() {
                const day = $(this).data('day');
                const isChecked = $(this).is(':checked');
                $('.class-time-slot[data-day="' + day + '"]').prop('checked', isChecked);
                updateClassRowCheckboxes();
            });

            // Update row checkboxes for class
            function updateClassRowCheckboxes() {
                $('.class-select-row').each(function() {
                    const slot = $(this).data('slot');
                    const allChecked = $('.class-time-slot[data-slot="' + slot + '"]').length === 
                                    $('.class-time-slot[data-slot="' + slot + '"]:checked').length;
                    $(this).prop('checked', allChecked);
                });
            }

            // Update column checkboxes for class
            function updateClassColumnCheckboxes() {
                $('.class-select-column').each(function() {
                    const day = $(this).data('day');
                    const allChecked = $('.class-time-slot[data-day="' + day + '"]').length === 
                                    $('.class-time-slot[data-day="' + day + '"]:checked').length;
                    $(this).prop('checked', allChecked);
                });
            }

            // Update checkboxes when individual class time slots are clicked
            $('.class-time-slot').change(function() {
                updateClassRowCheckboxes();
                updateClassColumnCheckboxes();
            });

            $('#constraintClass').change(function() {
                const classGroup = $(this).val();
                if (classGroup) {
                    loadClassConstraints(classGroup);
                } else {
                    resetClassConstraintForm();
                }
            });

            function loadClassConstraints(classGroup) {
                $.ajax({
                    url: '${createLink(controller: "timetable", action: "getClassConstraints")}',
                    data: { classGroup: classGroup },
                    success: function(response) {
                        if (response.success) {
                            console.log("Loading constraints for class", classGroup, response);
                            // Set time slots
                            $('.class-time-slot').each(function() {
                                const day = $(this).data('day');
                                const slot = $(this).data('slot');
                                const isChecked = response.availableSlots[day]?.includes(slot) || false;
                                $(this).prop('checked', isChecked);
                            });
                            
                            // Update row and column selectors
                            updateClassRowCheckboxes();
                            updateClassColumnCheckboxes();
                        } else {
                            console.error("Error loading class constraints:", response.message);
                            alert('Error loading class constraints: ' + response.message);
                        }
                    },
                    error: function(xhr, status, error) {
                        console.error("AJAX error:", error);
                        alert('Error loading class constraints: ' + error);
                    }
                });
            }

            $('#saveClassConstraints').click(function() {
                const classGroup = $('#constraintClass').val();
                if (!classGroup) {
                    alert('Please select a class');
                    return;
                }
                
                // Collect available slots
                const availableSlots = {};
                const workingDays = new Set();
                
                data.weekDays.forEach(day => {
                    availableSlots[day] = [];
                    
                    // Get all checked slots for this day
                    $('.class-time-slot[data-day="' + day + '"]:checked').each(function() {
                        const slot = $(this).data('slot');
                        availableSlots[day].push(slot);
                        if (slot) {
                            workingDays.add(day);
                        }
                    });
                });
                
                console.log("Saving constraints for class", classGroup, {
                    workingDays: Array.from(workingDays),
                    availableSlots: availableSlots
                });
                
                // Save constraints
                $.ajax({
                    url: '${createLink(controller: "timetable", action: "saveClassConstraints")}',
                    method: 'POST',
                    contentType: 'application/json',
                    data: JSON.stringify({
                        classGroup: classGroup,
                        workingDays: Array.from(workingDays),
                        availableSlots: availableSlots
                    }),
                    success: function(response) {
                        if (response.success) {
                            alert('Class constraints saved successfully');
                        } else {
                            console.error("Error saving class constraints:", response.message);
                            alert('Error saving class constraints: ' + response.message);
                        }
                    },
                    error: function(xhr, status, error) {
                        console.error("AJAX error:", error);
                        alert('Error saving class constraints: ' + error);
                    }
                });
            });

            function resetClassConstraintForm() {
                $('.class-time-slot').prop('checked', true);
                updateClassRowCheckboxes();
                updateClassColumnCheckboxes();
            }

            // Initialize first class's constraints
            if ($('#constraintClass option').length > 0) {
                $('#constraintClass').prop('selectedIndex', 0).trigger('change');
            }

            $('#type').change(function() {
                if ($(this).val() === 'Lab' || $(this).val() === 'Tutorial') {
                    $('.batch-field').show();
                    $('#batch').prop('required', true);
                } else {
                    $('.batch-field').hide();
                    $('#batch').prop('required', false);
                }
            });

            $('#roomAllocation').change(function() {
                if ($(this).val() === 'manual') {
                    $('#manualRoomSelection').show();
                    $('#manualRoom').prop('required', true);
                } else {
                    $('#manualRoomSelection').hide();
                    $('#manualRoom').prop('required', false);
                }
            });

            // Select All checkbox functionality
            $('#selectAll').change(function() {
                $('.subject-checkbox').prop('checked', $(this).is(':checked'));
                updateDeleteButtonVisibility();
            });

            // Individual checkbox change handler
            $(document).on('change', '.subject-checkbox', function() {
                updateDeleteButtonVisibility();
                
                // Update "Select All" checkbox
                var allChecked = $('.subject-checkbox:checked').length === $('.subject-checkbox').length;
                $('#selectAll').prop('checked', allChecked);
            });

            // Function to show/hide delete button
            function updateDeleteButtonVisibility() {
                var checkedCount = $('.subject-checkbox:checked').length;
                $('#deleteSelectedBtn').toggle(checkedCount > 0);
            }

            // Delete selected subjects
            $('#deleteSelectedBtn').click(function() {
                var selectedKeys = $('.subject-checkbox:checked').map(function() {
                    return $(this).data('key');
                }).get();

                if (selectedKeys.length === 0) {
                    alert('Please select at least one subject to delete');
                    return;
                }

                if (confirm('Are you sure you want to delete ' + selectedKeys.length + ' selected subjects?')) {
                    deleteSelectedSubjects(selectedKeys);
                }
            });

            // Function to delete multiple subjects
            function deleteSelectedSubjects(keys) {
                $.ajax({
                    url: '${createLink(controller: 'timetable', action: 'deleteMultipleSubjects')}',
                    method: 'POST',
                    contentType: 'application/json',
                    data: JSON.stringify({ keys: keys }),
                    success: function(response) {
                        if (response.success) {
                            location.reload();
                        } else {
                            alert('Error: ' + (response.message || 'Failed to delete subjects'));
                        }
                    },
                    error: function() {
                        alert('Error deleting subjects');
                    }
                });
            }

            // Initialize lecture cards
            updateLectureCardsView(${raw((lectureCards as JSON).toString())});

            $('#displayButton').click(function() {
                var selectedType = $('#dropdown1').val();
                var selectedValue = $('#dropdown2').val();

                if (!selectedValue) {
                    alert('Please select an option from the second dropdown.');
                    return;
                }
                $.ajax({
                    url: '${createLink(controller: 'timetable', action: 'getTimetableForEntity')}',
                    method: 'GET',
                    data: { 
                        type: selectedType,
                        value: selectedValue
                    },
                    success: function(response) {
                        if (response.success) {
                            updateStep3TimetableView(response.timetable, selectedType, selectedValue);
                        } else {
                            alert('Error fetching timetable: ' + (response.message || 'Unknown error'));
                        }
                    },
                    error: function(jqXHR, textStatus, errorThrown) {
                        console.error("AJAX error:", jqXHR, textStatus, errorThrown);
                        alert('Failed to fetch timetable. Server responded with status: ' + jqXHR.status + '\nError: ' + errorThrown);
                    }
                });
            });
            
            function updateStep3TimetableView(timetable, selectedType, selectedValue) {
                $('#step3TimetableTitle').text('Timetable for ' + getEntityTypeLabel(selectedType) + ': ' + selectedValue);
                
                var tbody = $('#step3Timetable tbody');
                tbody.empty();

                data.weekDays.forEach(function(day) {
                    var row = $('<tr>').append($('<th>').text(day));
                    data.timeSlots.forEach(function(time) {
                        var cell = $('<td>').addClass('timetable-cell');
                        var sessions = timetable[day] && timetable[day][time] ? timetable[day][time] : [];
                        
                        if (sessions.length > 0) {
                            sessions.forEach(function(session) {
                                var content = $('<div>').css({
                                    'padding': '5px',
                                    'margin-bottom': '5px'
                                });
                                content.append('Class: ' + (session.class || 'N/A') + '<br>');
                                if (session.type !== 'Lecture') {
                                    content.append('Batch: ' + (session.batch || 'N/A') + '<br>');
                                }
                                content.append('Subject: ' + (session.subject || 'N/A') + '<br>' +
                                        'Teacher: ' + (session.teacher || 'N/A') + '<br>' +
                                        'Room: ' + (session.room || 'TBD') + '<br>' +
                                        'Type: ' + (session.type || 'N/A') + '<br>');
                                if (session.type === 'Lab') {
                                    content.append('(2 hours)<br>');
                                }
                                cell.append(content);
                            });
                        } else {
                            cell.text('-');
                        }
                        row.append(cell);
                    });
                    tbody.append(row);
                });

                $('#step3TimetableContainer').show();
            }

            function getEntityTypeLabel(selectedType) {
                switch(selectedType) {
                    case 'option1': return 'Class';
                    case 'option2': return 'Teacher';
                    case 'option3': return 'Lab';
                    case 'option4': return 'Room';
                    default: return 'Entity';
                }
            }

            function initializeDropdown2() {
                $('#dropdown2').empty();
                $.each(data.classes, function(index, value) {
                    $('#dropdown2').append('<option value="' + value + '">' + value + '</option>');
                });
            }

            initializeDropdown2();

            $('#dropdown1').change(function() {
                var selectedOption = $(this).val();
                var options = [];

                switch(selectedOption) {
                    case 'option1':
                        options = data.classes;
                        break;
                    case 'option2':
                        options = data.teachers;
                        break;
                    case 'option3':
                        options = data.classrooms.concat(data.tutorialRooms).concat(data.labs); // Include labs here
                        break;
                }

                $('#dropdown2').empty();
                $.each(options, function(index, value) {
                    $('#dropdown2').append('<option value="' + value + '">' + value + '</option>');
                });
            });

            // Show download button when timetable is displayed
            $('#displayButton').click(function() {
                $('#downloadEntityTimetable').show();
            });

            $('#downloadEntityTimetable').click(function() {
                var entityType = $('#dropdown1').val();
                var entityValue = $('#dropdown2').val();
                
                window.location.href = '${createLink(controller: "timetable", action: "downloadEntityTimetable")}' +
                    '?type=' + entityType + '&value=' + encodeURIComponent(entityValue);
            });

            // Class filter change handler
            $('#selectedClass').change(function() {
                $('#filterForm').submit();
            });
            
            // Lecture card selection and assignment
            let selectedLectureCard = null;

            $('.lecture-card').click(function() {
                $('.lecture-card').removeClass('selected');
                $(this).addClass('selected');
                selectedLectureCard = $(this).data('id');
            });

            $('.timetable-cell').click(function() {
                if (selectedLectureCard) {
                    let day = $(this).data('day');
                    let time = $(this).data('time');
                    console.log("Cell clicked:", day, time, selectedLectureCard); // Debug log
                    if (!day || !time) {
                        alert("Error: Unable to determine day or time for the selected cell.");
                        return;
                    }
                    assignLecture(selectedLectureCard, day, time);
                } else {
                    alert("Please select a lecture card first.");
                }
            });

            function assignLecture(lectureId, day, time) {
                $.ajax({
                    url: '${createLink(controller: 'timetable', action: 'assignLecture')}',
                    method: 'POST',
                    contentType: 'application/json',
                    data: JSON.stringify({
                        selectedClass: $('#selectedClass').val(),
                        lectureId: lectureId,
                        day: day,
                        time: time
                    }),
                    success: function(response) {
                        if (response.success) {
                            updateTimetableCell(day, time, response.lecture);
                            if (response.lecture.type === 'Lab' && response.nextSlot) {
                                updateTimetableCell(day, response.nextSlot, response.lecture);
                            }
                            updateLectureCardCount(lectureId, response.remainingCount);
                        } else {
                            // Show a more user-friendly alert for constraint violations
                            let errorMessage = response.message;
                            if (errorMessage.includes('not available')) {
                                errorMessage = `Cannot assign lecture:\n${errorMessage}\n\nPlease check teacher availability constraints.`;
                            }
                            alert(errorMessage);
                        }
                    },
                    error: function(jqXHR, textStatus, errorThrown) {
                        console.error("AJAX error:", jqXHR, textStatus, errorThrown);
                        alert('Failed to assign lecture. Error: ' + errorThrown);
                    }
                });
            }

            $('#generateTimetableBtn').click(function(e) {
                e.preventDefault();
                $.ajax({
                    url: '${createLink(controller: 'timetable', action: 'generateTimetable')}',
                    method: 'POST',
                    data: { selectedClass: $('#selectedClass').val() },
                    success: function(response) {
                        if (response.success) {
                            if (response.lectureCards) {
                                updateLectureCardsView(response.lectureCards);
                            }
                            updateTimetableView(response.timetable);
                            
                            if (response.warnings) {
                                let warningMessage = response.warnings.join('\n');
                                alert('Timetable generated with following warnings:\n\n' + warningMessage);
                            }
                        } else {
                            alert('Error generating timetable: ' + (response.message || 'Unknown error'));
                        }
                    },
                    error: function(jqXHR, textStatus, errorThrown) {
                        console.error("AJAX error:", jqXHR, textStatus, errorThrown);
                        alert('Failed to generate timetable. Server responded with status: ' + 
                            jqXHR.status + '\nError: ' + errorThrown);
                    }
                });
            });

            function updateTimetableView(timetable) {
                $('.timetable-cell').each(function() {
                    const day = $(this).data('day');
                    const time = $(this).data('time');
                    const sessions = timetable[day] && timetable[day][time] ? timetable[day][time] : [];

                    let content = sessions.length > 0 ? '' : '-';  // Default content for empty slots

                    sessions.forEach((session) => {
                        content += '<div style="padding: 5px; margin-bottom: 5px;">';
                        content += (session.type !== 'Lecture' ? 'Batch ' + (session.batch || 'N/A') + '<br>' : '') +
                                'Subject: ' + (session.subject || 'N/A') + '<br>' +
                                'Teacher: ' + (session.teacher || 'N/A') + '<br>' +
                                'Room: ' + (session.room || 'TBD') + '<br>' +
                                'Type: ' + (session.type || 'N/A') + '<br>';
                        if (session.type === 'Lab') {
                            content += '(2 hours)<br>';
                        }
                        content += '</div>';
                    });
                    $(this).html(content);
                });
            }

            function updateLectureCardsView(lectureCards) {
                $('#lectureCards').empty();
                if (!lectureCards || lectureCards.length === 0) {
                    console.warn("No lecture cards to display");
                    return;
                }
                
                lectureCards.forEach(function(card) {
                    var isDisabled = card.count <= 0;
                    var cardHtml = '<div class="col-md-3 mb-2">' +
                        '<div class="lecture-card shadow p-3 rounded' + (isDisabled ? ' disabled' : '') + '" data-id="' + card.id + '">' +
                        '<h6>' + card.subject + ' (' + card.type + ')</h6>' +
                        '<h6>Teacher: ' + card.teacher + '</h6>';
                    
                    if (card.type !== 'Lecture') {
                        cardHtml += '<h6>Batch: ' + card.batch + '</h6>';
                    }
                    
                    cardHtml += '<h6>Room: ' + (card.roomAllocation === 'automatic' ? 'Automatic' : card.manualRoom) + '</h6>' +
                        '<h6>Remaining: <span class="lecture-count">' + card.count + '</span></h6>' +
                        '</div>' +
                        '</div>';
                    
                    $('#lectureCards').append(cardHtml);
                });
            }

            function updateTimetableCell(day, time, lecture) {
                let cell = $('.timetable-cell[data-day="' + day + '"][data-time="' + time + '"]');
                let currentContent = cell.html().trim();

                if (lecture) {
                    let newContent = '<div style="padding: 5px; margin-bottom: 5px;">';
                    if (lecture.type !== 'Lecture') {
                        newContent += 'Batch ' + (lecture.batch || 'N/A') + '<br>';
                    }
                    newContent += 'Subject: ' + (lecture.subject || 'N/A') + '<br>' +
                                'Teacher: ' + (lecture.teacher || 'N/A') + '<br>' +
                                'Room: ' + (lecture.room || 'TBD') + '<br>' +
                                'Type: ' + (lecture.type || 'N/A');
                    
                    if (lecture.type === 'Lab') {
                        newContent += ' (2 hours)';
                    }
                    
                    newContent += '</div>';

                    if (currentContent !== '-') {
                        cell.append(newContent);
                    } else {
                        cell.html(newContent);
                    }
                } else {
                    cell.html('-');
                }
            }

            function updateLectureCardCount(lectureId, remainingCount) {
                let card = $('.lecture-card[data-id="' + lectureId + '"]');
                let countSpan = card.find('.lecture-count');
                countSpan.text(remainingCount);
                
                // Disable card if no remaining lectures
                if (remainingCount <= 0) {
                    card.addClass('disabled');
                    card.draggable('disable');  // If using jQuery UI draggable
                }
            }

            $('form[action$="/resetTimetable"]').submit(function(e) {
                e.preventDefault();
                var currentStep = $('#currentStepInput').val();
                var selectedClass = $('#selectedClass').val();
                
                $.ajax({
                    url: this.action,
                    method: 'POST',
                    data: {
                        currentStep: currentStep,
                        selectedClass: selectedClass
                    },
                    success: function(response) {
                        if (response.success) {
                            // Reload the page with the same step and class
                            window.location.href = '${createLink(controller: "timetable", action: "index")}' +
                                '?currentStep=' + currentStep + '&selectedClass=' + selectedClass;
                        } else {
                            alert('Error resetting timetable: ' + response.message);
                        }
                    },
                    error: function(jqXHR, textStatus, errorThrown) {
                        console.error("AJAX error:", jqXHR, textStatus, errorThrown);
                        alert('Failed to reset timetable. Error: ' + errorThrown);
                    }
                });
            });
        });
    </script>
</body>
</html>