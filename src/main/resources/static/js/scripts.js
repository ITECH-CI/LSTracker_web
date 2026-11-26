
// =====================================================
// Jeton CSRF des requêtes jQuery qui modifient des données ($.post,
// $.ajax POST, PUT, PATCH, DELETE) : Spring Security le publie dans le
// cookie XSRF-TOKEN (non HttpOnly) et l'attend en retour dans l'en-tête
// X-XSRF-TOKEN, sinon il répond 403. Les formulaires Thymeleaf reçoivent
// déjà le champ _csrf.
//
// Ce fichier est chargé dans l'en-tête commun (fragments/header :: head),
// avant jQuery, qui est chargé en bas de page : l'installation attend la
// fin du chargement du document. Elle a lieu une seule fois par page (un
// en-tête envoyé deux fois serait refusé). Aucun code de ce fichier ne
// doit utiliser jQuery au chargement.
// =====================================================
function getCsrfTokenFromCookie() {
	const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
	return m ? decodeURIComponent(m[1]) : null;
}

function installCsrfPrefilter() {
	if (!window.jQuery || window.LST_CSRF_PREFILTER) return;
	window.LST_CSRF_PREFILTER = true;
	jQuery.ajaxPrefilter(function(options, originalOptions, jqXHR) {
		const method = (options.type || options.method || 'GET').toUpperCase();
		if (method === 'GET' || method === 'HEAD' || method === 'OPTIONS') return;
		const token = getCsrfTokenFromCookie();
		if (token) jqXHR.setRequestHeader('X-XSRF-TOKEN', token);
	});
}

// Enregistré avant le chargement de jQuery : passe avant les $(function () {...})
// des pages, donc avant leurs premiers appels AJAX.
document.addEventListener('DOMContentLoaded', function() {
	installCsrfPrefilter();
	if (window.jQuery && jQuery.fn.DataTable && document.getElementById('datatablesSimple')) {
		jQuery('#datatablesSimple').DataTable({
			dom: 'Bfrtip',
			buttons: ['csv', 'excel', 'pdf']
		});
	}
});


setPage = function(num) {
	const parser = new URL(window.location)
	if (num) {
		parser.searchParams.set('page', num)
	}
	window.location = parser.href
}


setSize = function(element) {
	const parser = new URL(window.location)
	var num = element.value;
	if (num) {
		parser.searchParams.set('size', num)
		parser.searchParams.set('page', 1)
	}
	window.location = parser.href
}


filterData = function() {
	let date1 = $('#input_start_date').val();
	let date2 = $('#input_end_date').val();
	let region = $('#select_region').val()
	let site = $('#select_site').val();
	let district = $('#select_district').val();
	let lab = $('#select_lab').val();
	const parser = new URL(window.location)
	parser.searchParams.set('page', 1)

	if (date1) {
		parser.searchParams.set('start', date1)
	} else {
		parser.searchParams.delete('start')
	}
	if (date2) {
		parser.searchParams.set('end', date2)
	} else {
		parser.searchParams.delete('end')
	}
	if (region) {
		if (district) {
			if (site) {
				parser.searchParams.delete('district');
				parser.searchParams.delete('region');
				parser.searchParams.set('site', site);
			}
			else {
				parser.searchParams.delete('site');
				parser.searchParams.delete('region');
				parser.searchParams.set('district', district);
			}
		} else {
			parser.searchParams.delete('district');
			parser.searchParams.delete('site');
			parser.searchParams.set('region', region);
		}
	}
	else {
		parser.searchParams.delete('region');
	}
	if (site) {
		parser.searchParams.delete('district');
		parser.searchParams.delete('region');
		parser.searchParams.set('site', site);
	}
	else {
		parser.searchParams.delete('site');
	}
	if (district) {
		if (site) {
			parser.searchParams.delete('district');
			parser.searchParams.delete('region');
			parser.searchParams.set('site', site);
		}
		else {
			parser.searchParams.delete('site');
			parser.searchParams.delete('region');
			parser.searchParams.set('district', district);
		}
	} else {
		parser.searchParams.delete('district');
	}
	if (lab) {
		parser.searchParams.set('lab', lab);
	} else {
		parser.searchParams.delete('lab', lab);
	}

	window.location = parser.href
}


filterSampleList = function() {
	let date1 = $('#input_start_date').val();
	let date2 = $('#input_end_date').val();
	let region = $('#select_region').val()
	let site = $('#select_site').val();
	let lab = $('#select_lab').val();
	let district = $('#select_district').val();
	let status = $('#select_status').val();
	let sampleType = $('#select_sample_type').val();
	let patientIdentifier = $('#select_patient_identifier').val();
	const parser = new URL(window.location)
	parser.searchParams.set('page', 1)

	if (status) {
		parser.searchParams.set('status', status)
	} else {
		parser.searchParams.delete('status')
	}
	if (date1) {
		parser.searchParams.set('start', date1)
	} else {
		parser.searchParams.delete('start')
	}
	if (date2) {
		parser.searchParams.set('end', date2)
	} else {
		parser.searchParams.delete('end')
	}
	if (sampleType) {
		parser.searchParams.set('sample_type', sampleType)
	} else {
		parser.searchParams.delete('sample_type')
	}
	if (patientIdentifier) {
		parser.searchParams.set('patient_identifier', patientIdentifier)
	} else {
		parser.searchParams.delete('patient_identifier')
	}
	if (region) {
		if (district) {
			if (site) {
				parser.searchParams.delete('district');
				parser.searchParams.delete('region');
				parser.searchParams.set('site', site);
			}
			else {
				parser.searchParams.delete('site');
				parser.searchParams.delete('region');
				parser.searchParams.set('district', district);
			}
		} else {
			parser.searchParams.delete('district');
			parser.searchParams.delete('site');
			parser.searchParams.set('region', region);
		}
	}
	else {
		parser.searchParams.delete('region');
	}
	if (site) {
		parser.searchParams.delete('district');
		parser.searchParams.delete('region');
		parser.searchParams.set('site', site);
	}
	else {
		parser.searchParams.delete('site');
	}
	if (lab) {
		parser.searchParams.set('lab', lab);
	}
	else {
		parser.searchParams.delete('lab');
	}
	if (district) {
		if (site) {
			parser.searchParams.delete('district');
			parser.searchParams.delete('region');
			parser.searchParams.set('site', site);
		}
		else {
			parser.searchParams.delete('site');
			parser.searchParams.delete('region');
			parser.searchParams.set('district', district);
		}
	} else {
		parser.searchParams.delete('district');
	}

	window.location = parser.href
}

resetFilter = function() {
	const parser = new URL(window.location);
	parser.searchParams.delete('page');
	parser.searchParams.delete('lab');
	parser.searchParams.delete('site');
	parser.searchParams.delete('district');
	parser.searchParams.delete('region');
	parser.searchParams.delete('start');
	parser.searchParams.delete('end');
	parser.searchParams.delete('status');
	parser.searchParams.delete('patient_identifier');
	parser.searchParams.delete('sample_type');
	window.location = parser.href
}

sortData = function(sortItem) {
	const parser = new URL(window.location)
	let existingSortQuery = parser.searchParams.get('sort')
	let sortDirectionString = 'desc'
	if (!parser.searchParams.has('sort')) {
		sortDirectionString = sortDirection()
	}
	else {
		existingSortItem = existingSortQuery.split(',')[0]
		if (existingSortItem === sortItem) {
			sortDirectionString = sortDirection(true)
		}
		else {
			sortDirectionString = sortDirection()
		}
	}
	parser.searchParams.set('sort', sortItem + ',' + sortDirectionString)
	window.location = parser.href
}

function sortDirection(next = false) {
	let directions = ['desc', 'asc']
	sortIndex = localStorage.getItem("sortIndex")
	if (!sortIndex) {
		sortIndex = 0
		localStorage.setItem("sortIndex", sortIndex)
	}
	else {
		if (next) {
			sortIndex = (parseInt(sortIndex) + 1) % 2
		}
		localStorage.setItem("sortIndex", sortIndex)
	}
	return directions[sortIndex]
}

exportToCSV = function() {
	const parser = new URL(window.location)
	var newLocation = parser.href.replace('sample', 'sample/csv')
	window.location = newLocation
}

exportToPDF = function() {
	const parser = new URL(window.location)
	var newLocation = parser.href.replace('sample', 'sample/pdf')
	window.location = newLocation
}
