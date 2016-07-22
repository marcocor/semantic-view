var filterEntities = []
var MAX_ENTITY_FONT_SIZE = 260

$(document).ready(function() {
	updateFilterMenu()
	fillIn();
});

function updateFilterMenu() {
	$("#filter-buttons").empty()
	$.each(filterEntities, function(i, entity) {
		$("#filter-buttons").append($("<button>").attr("type", "button").attr("class", "btn btn-primary").attr("entity", entity).click(buttonClicked).html(entity))
	})
}

function docIdClicked() {
	var docId = $(this).attr("doc-id")
	$("#document-body").empty()
	$.getJSON("rest/document", {
		docId : docId,
	}).done(function(data) {
		$("#document-title").html(data.title)
		$("#document-body").html(data.body)
	})
	$("#document-modal").modal()
}

function buttonClicked() {
	var entity = $(this).attr("entity")
	filterEntities.splice(filterEntities.indexOf(entity), 1);

	updateFilterMenu()
	fillIn()
}

function entityClicked(item) {
	var entity = item[0]

	if (filterEntities.indexOf(entity) < 0)
		filterEntities.push(entity);

	updateFilterMenu()
	fillIn()
}

function fillIn() {

	var listFrequencies = [];

	var frequencyAPI = "rest/frequency";
	$.getJSON(frequencyAPI, {
		entities : filterEntities.join("$$$"),
		limit:100,
	}).done(
			function(data) {
				var maxFreq = -1
				$.each(data.frequencies, function(i, freq) {
					if (freq.frequency > maxFreq)
						maxFreq = freq.frequency
				});
				
				$.each(data.frequencies, function(i, freq) {
					listFrequencies.push([ freq.entity, freq.frequency / maxFreq * MAX_ENTITY_FONT_SIZE ])
				});

				$("#document-ids-list").empty()
				$.each(data.document_ids.slice(0, 20), function(i, docId) {
					$("#document-ids-list").append($("<li>").attr("class", "list-group-item").attr("doc-id", docId).click(docIdClicked).html(docId))
				});

				$("#n-entities").html($("<strong>").html(data.entities))
				$("#n-documents").html($("<strong>").html(data.documents))

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