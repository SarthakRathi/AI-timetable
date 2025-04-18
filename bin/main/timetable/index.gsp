<!-- File: grails-app/views/timetable/index.gsp -->
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
    </style>
</head>
<body>
    <div class="container mt-3">
        <h1 class="text-center mb-4">Timetable Generator</h1>

        <!-- Horizontal Stepper -->
        <div class="d-flex justify-content-between mb-4">
            <div class="stepper-item active" data-step="1">Step 1: Subject Mapping</div>
            <div class="stepper-item" data-step="2">Step 2: Timetable Generation</div>
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
                    <label for="class" class="form-label">Class:</label>
                    <select id="class" name="class" class="form-select" required>
                        <option value="">Select a class</option>
                        <g:each in="${classes}" var="classOption">
                            <option value="${classOption}">${classOption}</option>
                        </g:each>
                    </select>
                </div>
                <div class="mb-3">
                    <label for="lecturesPerWeek" class="form-label">Number of Lectures per Week:</label>
                    <input type="number" id="lecturesPerWeek" name="lecturesPerWeek" class="form-control" required min="1">
                </div>
                <button type="submit" class="btn btn-primary">Add Subject</button>
            </form>

            <!-- Table to display subject details -->
            <h2>Subject Details</h2>
            <table class="table table-bordered table-hover shadow-sm p-3 mb-3 bg-white rounded">
                <thead>
                    <tr>
                        <th>Subject</th>
                        <th>Type</th>
                        <th>Teacher</th>
                        <th>Class</th>
                        <th>Lectures per Week</th>
                        <th>Delete</th>
                    </tr>
                </thead>
                <tbody>
                    <g:each in="${subjectDetails}" var="entry">
                        <tr>
                            <td>${entry.value.subject}</td>
                            <td>${entry.value.type}</td>
                            <td>${entry.value.teacher}</td>
                            <td>${entry.value.class}</td>
                            <td>${entry.value.lecturesPerWeek}</td>
                            <td><i class="bi bi-trash3-fill" onClick="deleteSubject('${entry.key}')"></i></td>
                        </tr>
                    </g:each>
                </tbody>
            </table>
        </div>

        <!-- Step 2: Timetable Generation -->
        <div class="step-content" id="step2">
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
                        <div class="lecture-card shadow p-3 bg-white rounded" data-id="${card.id}">
                            <h6>${card.subject} (${card.type})</h6>
                            <h6>Teacher: ${card.teacher}</h6>
                            <h6>Remaining: <span class="lecture-count">${card.count}</span></h6>
                        </div>
                    </div>
                </g:each>
            </div>                                                                                                                                                                                                  

            <!-- Timetable display -->
            <h2>Current Timetable for ${selectedClass}</h2>
            <table id="timetable" class="table table-bordered table-hover table-striped shadow-sm p-3 mb-3 bg-white rounded">
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
                            <g:each in="${timeSlots}" var="time">
                                <td class="timetable-cell" data-day="${day}" data-time="${time}">
                                    <g:if test="${timetable[day]?.get(time)?.size() > 0}">
                                        <g:each in="${timetable[day]?.get(time)}" var="session" status="i">
                                            <g:if test="${session}">
                                                Batch ${i + 1}<br>
                                                Subject: ${session.subject ?: 'N/A'}<br>
                                                Teacher: ${session.teacher ?: 'N/A'}<br>
                                                Room: ${session.room ?: 'TBD'}<br>
                                                Type: ${session.type ?: 'N/A'}<br><br>
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

        <!-- Navigation Buttons -->
        <div class="d-flex justify-content-between mt-4">
            <button id="prevBtn" class="btn btn-secondary" style="display: none;">Previous</button>
            <button id="nextBtn" class="btn btn-primary">Next</button>
        </div>
    </div>

    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script>

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
            let currentStep = ${params.currentStep ? params.currentStep : 1};
            const totalSteps = 2;

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
                currentStep = $(this).data('step');
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
            console.log("Assigning lecture:", lectureId, day, time); // Debug log

            if (!lectureId || !day || !time) {
                alert("Error: Missing lecture information. Please try again.");
                return;
            }

            var selectedClass = '${selectedClass}';
            if (!selectedClass) {
                alert("Error: No class selected. Please select a class and try again.");
                return;
            }

            $.ajax({
                url: '${createLink(controller: 'timetable', action: 'assignLecture')}',
                method: 'POST',
                contentType: 'application/json',
                data: JSON.stringify({
                    selectedClass: selectedClass,
                    lectureId: lectureId,
                    day: day,
                    time: time
                }),
                success: function(response) {
                    if (response.success) {
                        updateTimetableCell(day, time, response.lecture);
                        updateLectureCardCount(lectureId);
                    } else {
                        alert('Error assigning lecture: ' + response.message);
                    }
                },
                error: function(jqXHR, textStatus, errorThrown) {
                    console.error("AJAX error:", jqXHR, textStatus, errorThrown); // Debug log
                    var errorMessage = 'Error assigning lecture: ' + errorThrown + 
                                    '\nStatus: ' + jqXHR.status + 
                                    '\nResponse: ' + jqXHR.responseText;
                    console.error(errorMessage); // Log to console
                    alert(errorMessage);
                }
            });
        }

        $('#generateTimetableBtn').click(function(e) {
            e.preventDefault();
            $.ajax({
                url: '${createLink(controller: 'timetable', action: 'generateTimetable')}',
                method: 'POST',
                data: { selectedClass: $('#selectedClass').val() }, // Ensure correct class is sent
                success: function(response) {
                    if (response.success) {
                        updateTimetableView(response.timetable); // Update the view with new timetable
                        updateLectureCardsView(response.lectureCards);
                    } else {
                        alert('Error generating timetable: ' + (response.message || 'Unknown error'));
                    }
                },
                error: function(jqXHR) {
                    alert('Failed to generate timetable. Server responded with status: ' + jqXHR.status);
                }
            });
        });


        function updateTimetableView(timetable) {
            // Iterate through each day and time slot to update timetable cells
            $('.timetable-cell').each(function() {
                const day = $(this).data('day');
                const time = $(this).data('time');
                const sessions = timetable[day] && timetable[day][time] ? timetable[day][time] : [];

                let content = sessions.length > 0 ? '' : '-';  // Default content for empty slots

                sessions.forEach((session, index) => {
                    content += 'Batch ' + (index + 1) + '<br>' +
                            'Subject: ' + (session.subject || 'N/A') + '<br>' +
                            'Teacher: ' + (session.teacher || 'N/A') + '<br>' +
                            'Room: ' + (session.room || 'TBD') + '<br>' +
                            'Type: ' + (session.type || 'N/A') + '<br><br>';
                });
                $(this).html(content);
            });
        }

        function updateLectureCardsView(lectureCards) {
            lectureCards.forEach(function(card) {
                let cardElement = $('.lecture-card[data-id="' + card.id + '"]');
                cardElement.find('.lecture-count').text(card.count);
            });
        }

            function updateTimetableCell(day, time, lecture) {
                let cell = $('.timetable-cell[data-day="' + day + '"][data-time="' + time + '"]');
                // Get current content of the cell to determine if it's the first entry or subsequent.
                let currentContent = cell.html().trim();

                // Determine the batch number by counting how many 'Batch' occurrences are there.
                let batchNumber = (currentContent.match(/Batch/g) || []).length + 1;

                if (lecture) {
                    let newContent = 'Batch ' + batchNumber + '<br>' +
                                    'Subject: ' + (lecture.subject || 'N/A') + '<br>' +
                                    'Teacher: ' + (lecture.teacher || 'N/A') + '<br>' +
                                    'Room: ' + (lecture.room || 'TBD') + '<br>' +
                                    'Type: ' + (lecture.type || 'N/A') + '<br><br>';

                    if (currentContent !== '-' && currentContent.includes('Subject:')) {
                        // Append to existing content
                        cell.append(newContent);
                    } else {
                        // Set new content if it's the first batch
                        cell.html(newContent);
                    }
                } else {
                    cell.html('-'); // Reset if no lecture data
                }
            }



            function updateLectureCardCount(lectureId) {
                let card = $('.lecture-card[data-id="' + lectureId + '"]');
                let countSpan = card.find('.lecture-count');
                let currentCount = parseInt(countSpan.text());
                if (currentCount > 0) {
                    countSpan.text(currentCount - 1);
                    if (currentCount - 1 === 0) {
                        card.removeClass('selected');
                        selectedLectureCard = null;
                    }
                }
            }
        });
    </script>
</body>
</html>