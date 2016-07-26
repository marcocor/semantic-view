var MAX_ENTITY_FONT_SIZE = 260
var MIN_ENTITY_FONT_SIZE = 8
var filterEntities = []
var ignoredEntities = []
var dateRangeBegin = null
var dateRangeEnd = null

$("#chart-placeholder").bind("plotselected", function(event, ranges) {
	dateRangeBegin = parseInt(ranges.xaxis.from.toFixed(1));
	dateRangeEnd = parseInt(ranges.xaxis.to.toFixed(1));
	redrawAll();
});

$("#chart-placeholder").bind("plotunselected", function(event) {
	dateRangeBegin = null;
	dateRangeEnd = null;
	redrawAll();
});

$("#show-ignored-entities").click(function() {
	$("#ignored-entities-modal").modal();
});

$("#clear-entity-filter-button").click(function(){
	filterEntities = [];
	redrawAll();
});

$(document).ready(redrawAll());

ignoreEntityButtonClicked(null);

function redrawAll() {
	updateFilterMenu()
	fillIn()
	populateChart()
	populateIgnoredEntities()
}

function populateIgnoredEntities(){
	$("#number-ignored-entities").html($("<strong>").html(ignoredEntities.length));
	
	$("#ignored-entities-list").empty();
	
	if (!ignoredEntities.length)
		$("#ignored-entities-list").append(
				$("<li>").attr("class", "list-group-item").html("No ignored entities."))
		
	$.each(ignoredEntities, function(i, entity) {
		$("#ignored-entities-list").append(
			$("<li>").attr("class", "list-group-item")
			.append(entity + " (")
			.append(
				$("<a>")
				.attr("href", "#")
				.click(function(){unignore(entity)})
				.html("re-enable")
			)
			.append(")")
		)
	})
}

function updateFilterMenu() {
	if (filterEntities.length)
		$("#clear-entity-filter-button").show()
	else
		$("#clear-entity-filter-button").hide()
	
	$("#filter-buttons").empty();
	$.each(filterEntities, function(i, entity) {
		$("#filter-buttons").append(
				$("<div>").attr("class", "btn-group")
				.append(
					$("<button>")
					.attr("type", "button")
					.attr("class", "btn btn-primary")
					.attr("entity", entity)
					.click(entityFilterButtonClicked)
					.html(entity)
					)
					.append(
							$("<button>")
							.attr("type", "button")
							.attr("class", "btn btn-primary dropdown-toggle")
							.attr("data-toggle", "dropdown")
							.attr("aria-haspopup", "true")
							.attr("aria-expanded", "false")
							.append($("<span>").attr("class", "caret"))
							.append($("<span>").attr("class", "sr-only").html("Toggle dropdown"))
					)
					.append (
							$("<ul>")
							.attr("class", "dropdown-menu")
							.append($("<li>").append(
									$("<a>")
									.attr("href", "#")
									.attr("entity", entity)
									.click(function(){ignoreEntityButtonClicked($(this).attr("entity"))})
									.html("Ignore entity")
									)
							)
					)
		)
	});
}

function docIdClicked() {
	var docId = $(this).attr("doc-id");
	$("#document-body").empty();
	$.getJSON("rest/document", {
		docId : docId,
	}).done(function(data) {
		$("#document-title").html(data.title);
		$("#document-body").html(data.body);
	}).fail(function(jqxhr, textStatus, error) {
		var err = textStatus + ", " + error;
		alert("Request Failed: " + err);
	});

	$("#document-modal").modal();
}

function entityFilterButtonClicked() {
	var entity = $(this).attr("entity")
	filterEntities.splice(filterEntities.indexOf(entity), 1);

	redrawAll();
}

function unignore(entity) {
	$.getJSON("rest/unignore", {
		entity : entity,
	}).done(function(data) {
		ignoredEntities = data.ignoredEntities;
		redrawAll();
	}).fail(function(jqxhr, textStatus, error) {
		var err = textStatus + ", " + error;
		alert("Request Failed: " + err);
	});
}

function ignoreEntityButtonClicked(entity) {
	if (filterEntities.indexOf(entity) >= 0)
		filterEntities.splice(filterEntities.indexOf(entity), 1);
	
	$.getJSON("rest/ignore", {
		entity : entity,
	}).done(function(data) {
		ignoredEntities = data.ignoredEntities;
		redrawAll();
	}).fail(function(jqxhr, textStatus, error) {
		var err = textStatus + ", " + error;
		alert("Request Failed: " + err);
	});
}

function entityClicked(item) {
	var entity = item[0]

	if (filterEntities.indexOf(entity) < 0)
		filterEntities.push(entity);

	redrawAll();
}

function getIsoDate(epoch) {
	var date = new Date(epoch);
	return date.toISOString().substring(0, 10);
}

function getDateRange() {
	return (dateRangeBegin == null ? "" : getIsoDate(dateRangeBegin)) + "~"
			+ (dateRangeEnd == null ? "" : getIsoDate(dateRangeEnd));
}

function getFrequencyFontSize(frequency, maxFreq) {
	return (frequency / maxFreq)
			* (MAX_ENTITY_FONT_SIZE - MIN_ENTITY_FONT_SIZE)
			+ MIN_ENTITY_FONT_SIZE;
}

function fillIn() {

	var listFrequencies = [];

	var frequencyAPI = "rest/frequency";
	$
	.getJSON(
			frequencyAPI,
			{
				entities : filterEntities.join("$$$"),
				limit : 100,
				dateRange : getDateRange(),
			})
			.done(
					function(data) {
						var maxFreq = -1
						$.each(data.frequencies, function(i, freq) {
							if (freq.frequency > maxFreq)
								maxFreq = freq.frequency;
						});

						$.each(data.frequencies,
								function(i, freq) {
									listFrequencies
									.push([
									       freq.entity,
									       getFrequencyFontSize(freq.frequency, maxFreq)
									       ]
									)
						});

						$("#document-ids-list").empty();
						$.each(data.document_ids.slice(0, 20), function(i, docId) {
							$("#document-ids-list").append(
									$("<button>")
									.attr("class", "btn btn-default")
									.attr("doc-id", docId)
									.click(docIdClicked)
									.html(docId))
						});

						$("#n-entities").html($("<strong>").html(data.entities));
						$("#n-documents").html($("<strong>").html(data.documents));

						var c = document.getElementById("main-navi-canvas");
						WordCloud(c, {
							list : listFrequencies,
							click : entityClicked,
						});

					}).fail(function(jqxhr, textStatus, error) {
						var err = textStatus + ", " + error;
						alert("Request Failed: " + err);
					});

}

function populateChart() {
	$.getJSON("rest/time-frequency", {
		entities : filterEntities.join("$$$"),
	}).done(function(data) {
		var options = {
				series : {
					lines : {
						show : true
					},
					points : {
						show : true
					}
				},
				legend : {
					noColumns : 1
				},
				xaxis : {
					mode : "time",
					tickDecimals : 0
				},
				yaxis : {
					min : 0
				},
				selection : {
					mode : "x"
				}
		};

		var plot = $.plot($("#chart-placeholder"), data, options);

	}).fail(function(jqxhr, textStatus, error) {
		var err = textStatus + ", " + error;
		alert("Request Failed: " + err);
	});
}