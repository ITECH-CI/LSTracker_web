// Tableau de bord (templates/home/index.html). Servi en statique avec
// empreinte de contenu : mis en cache par le navigateur, renouvelé à chaque
// modification.
	/* eslint-disable */
	$(function () {
		// Highcharts global config: French export menu + offline rendering
		if (window.Highcharts) {
			Highcharts.setOptions({
				// Axes et grille discrets : seules les données sont « fortes ».
				chart: { style: { fontFamily: 'inherit' } },
				xAxis: { lineColor: '#e5e7eb', tickColor: '#e5e7eb', labels: { style: { color: '#6b7280' } } },
				yAxis: { gridLineColor: '#f1f5f9', labels: { style: { color: '#6b7280' } }, title: { style: { color: '#6b7280', fontWeight: 'normal' } } },
				legend: { itemStyle: { color: '#374151', fontWeight: '500', fontSize: '11px' } },
				// Bulles lisibles : plus grandes, contrastées, ombrées.
				tooltip: { backgroundColor: '#ffffff', borderColor: '#c7d2fe', borderRadius: 8, borderWidth: 1,
					shadow: { color: 'rgba(15,23,42,0.18)', offsetX: 0, offsetY: 4, width: 12 },
					padding: 10, style: { fontSize: '13px', color: '#111827' },
					headerFormat: '<span style="font-size:13px;font-weight:700">{point.key}</span><br/>' },
				lang: {
					contextButtonTitle: 'Options du graphique',
					downloadJPEG: 'Télécharger en JPEG',
					downloadPDF: 'Télécharger en PDF',
					downloadPNG: 'Télécharger en PNG',
					downloadSVG: 'Télécharger en SVG',
					printChart: 'Imprimer',
					viewFullscreen: 'Plein écran',
					exitFullscreen: 'Quitter le plein écran',
					loading: 'Chargement...',
					noData: 'Aucune donnée à afficher',
					resetZoom: 'Réinitialiser le zoom',
					resetZoomTitle: 'Réinitialiser le niveau de zoom'
				},
				exporting: {
					enabled: true,
					fallbackToExportServer: false,
					sourceWidth: 1100,
					sourceHeight: 500,
					chartOptions: {
						title: { style: { fontSize: '14px' } },
						subtitle: { text: 'LSTracker — ' + new Date().toLocaleDateString('fr-FR'), style: { fontSize: '10px', color: '#9ca3af' } }
					},
					buttons: {
						contextButton: {
							menuItems: ['viewFullscreen', 'separator',
								'downloadPNG', 'downloadJPEG', 'downloadSVG', 'downloadPDF',
								'separator', 'printChart']
						}
					}
				}
			});
		}

		// Datepickers
		$('#input_start_date, #input_end_date').datepicker({
			changeMonth: true, changeYear: true, dateFormat: 'dd/mm/yy', showAnim: '', firstDay: 1
		});

		// Select2
		$('.select2').select2({ width: '100%', allowClear: true, placeholder: '— Tous —' });

		// Region → District → Site cascades
		$('#select_region').on('change', function() {
			const r = $(this).val();
			if (!r) return;
			$.get('district/names_by_regions?regions=' + r, function(districts) {
				const $d = $('#select_district');
				$d.empty().append('<option value=""></option>');
				$.each(districts, (i, d) => $d.append('<option value="' + d.id + '">' + d.name + '</option>'));
				$d.val(null).trigger('change');
			});
		});
		$('#select_district').on('change', function() {
			const d = $(this).val();
			if (!d) return;
			$.get('site/names_by_districts?districts=' + d, function(sites) {
				const $s = $('#select_site');
				$s.empty().append('<option value=""></option>');
				$.each(sites, (i, s) => $s.append('<option value="' + s.id + '">' + s.name + '</option>'));
				$s.val(null).trigger('change');
			});
		});

		// Date presets — remplissent les champs PUIS rechargent l'URL pour
		// persister les filtres (cf. applyFiltersToUrl).
		$('.date-presets .btn').on('click', function() {
			const p = $(this).data('preset');
			const today = new Date();
			const fmt = d => {
				const pad = n => String(n).padStart(2, '0');
				return pad(d.getDate()) + '/' + pad(d.getMonth()+1) + '/' + d.getFullYear();
			};
			// Début de la période calendaire en cours ; fin = aujourd'hui.
			const y = today.getFullYear(), m = today.getMonth();
			let s;
			if (p === 'today')         { s = today; }
			else if (p === 'week')     { s = new Date(today); s.setDate(today.getDate() - ((today.getDay() + 6) % 7)); } // lundi
			else if (p === 'month')    { s = new Date(y, m, 1); }
			else if (p === 'quarter')  { s = new Date(y, m - (m % 3), 1); }
			else if (p === 'semester') { s = new Date(y, m < 6 ? 0 : 6, 1); }
			else if (p === 'year')     { s = new Date(y, 0, 1); }
			$('#input_start_date').val(fmt(s));
			$('#input_end_date').val(fmt(today));
			// La période est transmise au calcul de la période précédente
			// (cf. DashboardController.funnelPrevious).
			window._period = p;
			applyFiltersToUrl();
		});

		// Appliquer / Reset : on PERSISTE les filtres dans l'URL (reload) plutôt
		// que de faire un loadAll() AJAX. Avantage : F5 / partage de lien
		// conservent les filtres.
		$('#btnApplyFilters').on('click', applyFiltersToUrl);
		$('#btnResetFilters').on('click', function() {
			window.location = '/dashboard';
		});

		// Toggle collapse du panneau filtres (clic sur l'en-tête)
		$('#dashboardFiltersHeader').on('click', function() {
			$('#dashboardFiltersPanel').toggleClass('collapsed');
		});
		$('#granularitySelect').on('change', function() {
			if (window._lastSeriesData) renderTimeSeries(window._lastSeriesData, this.value);
		});
		$('#showEmptyZones').on('change', function() {
			// Re-render top-level + collapse any expanded children (they were
			// fetched with the old filter, simpler to re-expand on demand)
			$('#drillTableBody tr[data-parent], #drillTableBody tr[data-ancestor]').remove();
			$('#drillTableBody .expander').each(function() { if ($(this).text() === '▼') $(this).text('▶'); });
			window._drillPage = 1;
			renderDrillRegions();
		});

		// Initialise les filtres depuis l'URL (pré-remplit les champs + selects
		// en cascade), PUIS charge les widgets. La cascade region→district→site
		// est asynchrone, d'où le chaînage par callbacks.
		initFiltersFromUrlThenLoad();
	});

	// --- Persistance des filtres dans l'URL ---

	// Construit l'URL avec les filtres actifs et recharge la page.
	function applyFiltersToUrl() {
		const p = new URLSearchParams();
		const sd = isoFromFr($('#input_start_date').val());
		const ed = isoFromFr($('#input_end_date').val());
		if (sd) p.set('startDate', sd);
		if (ed) p.set('endDate', ed);
		if (window._period) p.set('period', window._period);
		const region = $('#select_region').val();
		const district = $('#select_district').val();
		const site = $('#select_site').val();
		const lab = $('#select_lab').val();
		if (region) p.set('region', region);
		if (district) p.set('district', district);
		if (site) p.set('site', site);
		if (lab) p.set('lab', lab);
		const qs = p.toString();
		window.location = '/dashboard' + (qs ? '?' + qs : '');
	}

	// Bulle d'information attachée à la page : jamais coupée par un bloc
	// parent, retournée sous l'élément près du haut de l'écran, et ouverte au
	// tap sur tablette (le survol n'y existe pas).
	(function initTips() {
		const tip = $('<div id="dashTip" role="tooltip"></div>').appendTo('body');
		let current = null;
		function show(el) {
			current = el;
			tip.text($(el).attr('data-tip')).addClass('show');
			const r = el.getBoundingClientRect();
			const w = tip.outerWidth(), h = tip.outerHeight();
			let left = Math.min(Math.max(8, r.left + r.width / 2 - w / 2), window.innerWidth - w - 8);
			let top = r.top - h - 8;
			if (top < 8) top = r.bottom + 8; // pas la place au-dessus : en dessous
			tip.css({ left: left + 'px', top: top + 'px' });
			$(el).addClass('is-open');
		}
		function hide() {
			tip.removeClass('show');
			if (current) $(current).removeClass('is-open');
			current = null;
		}
		$(document).on('mouseenter focus', '[data-tip]', function() { show(this); })
			.on('mouseleave blur', '[data-tip]', hide)
			.on('click', '[data-tip]', function(e) {
				e.preventDefault(); e.stopPropagation();
				current === this ? hide() : show(this);
			})
			.on('click', hide);
		$(window).on('scroll resize', hide);
		$('[data-tip]').attr('tabindex', '0');
	})();

	function frFromDate(d) {
		const pad = n => String(n).padStart(2, '0');
		return pad(d.getDate()) + '/' + pad(d.getMonth() + 1) + '/' + d.getFullYear();
	}

	// Bandeau « Période analysée · Comparée à » : toujours visible, rempli dès
	// que la période précédente est connue (réponse de funnel-previous).
	const PERIOD_NAMES = { today: "Aujourd'hui", week: 'Semaine en cours', month: 'Mois en cours',
		quarter: 'Trimestre en cours', semester: 'Semestre en cours', year: 'Année en cours' };
	function renderPeriodBanner(prev) {
		const f = currentFilters();
		const range = 'du ' + frFromIso(f.startDate) + ' au ' + frFromIso(f.endDate);
		$('[data-period-name]').text((PERIOD_NAMES[window._period] || 'Période analysée') + ' :');
		$('[data-period-range]').text(range);
		$('[data-period-short]').text(frFromIso(f.startDate) + ' → ' + frFromIso(f.endDate));
		$('[data-period-prev]').text(prev && prev.prev_start
			? 'du ' + frFromIso(prev.prev_start) + ' au ' + frFromIso(prev.prev_end) : '—');
		$('[data-period-rule]').text(window._period
			? '(même nombre de jours de la période précédente)'
			: '(période de même durée juste avant)');
	}

	// ISO yyyy-MM-dd → format datepicker dd/mm/yyyy. Null-safe.
	function frFromIso(s) {
		if (!s) return '';
		const m = s.match(/^(\d{4})-(\d{2})-(\d{2})$/);
		return m ? (m[3] + '/' + m[2] + '/' + m[1]) : '';
	}

	// Lit les params de l'URL, pré-remplit les champs date + selects (cascade
	// async region→district→site), puis lance loadAll().
	function initFiltersFromUrlThenLoad() {
		const u = new URLSearchParams(window.location.search);
		const region = u.get('region');
		const district = u.get('district');
		const site = u.get('site');
		const lab = u.get('lab');

		// Dates (converties ISO→FR pour le datepicker). Sans dates dans l'URL,
		// les champs affichent la période par défaut appliquée par le serveur
		// (2 ans jusqu'à aujourd'hui) au lieu de rester vides.
		const today = new Date();
		const twoYearsAgo = new Date(today.getFullYear() - 2, today.getMonth(), today.getDate());
		$('#input_start_date').val(frFromIso(u.get('startDate')) || frFromDate(twoYearsAgo));
		$('#input_end_date').val(frFromIso(u.get('endDate')) || frFromDate(today));
		// Période calendaire choisie par un raccourci : bouton actif. Une date
		// modifiée à la main la fait oublier (comparaison à durée égale).
		// Liste fermée : une valeur arbitraire de l'URL cassait le sélecteur
		// jQuery ci-dessous (exception → page blanche).
		const PERIODS = ['today', 'week', 'month', 'quarter', 'semester', 'year'];
		const p = u.get('period');
		window._period = PERIODS.includes(p) ? p : null;
		if (window._period) {
			$('.date-presets .btn[data-preset="' + window._period + '"]').addClass('active');
		}
		$('#input_start_date, #input_end_date').on('change', function() {
			window._period = null;
			$('.date-presets .btn').removeClass('active');
		});

		// Lab : indépendant de la cascade
		if (lab) $('#select_lab').val(lab).trigger('change.select2');

		// Cascade géographique : il faut charger les options enfant avant de
		// pouvoir sélectionner district puis site. On chaîne les $.get.
		if (!region) {
			// Pas de filtre géo dans l'URL → charge direct
			loadAll();
			return;
		}

		$('#select_region').val(region).trigger('change.select2');

		if (!district) {
			loadAll();
			return;
		}

		// Charger les districts de la région, sélectionner, puis (si besoin) sites
		$.get('district/names_by_regions?regions=' + region, function(districts) {
			const $d = $('#select_district');
			$d.empty().append('<option value=""></option>');
			$.each(districts, (i, d) => $d.append('<option value="' + d.id + '">' + d.name + '</option>'));
			$d.val(district).trigger('change.select2');

			if (!site) {
				loadAll();
				return;
			}

			$.get('site/names_by_districts?districts=' + district, function(sites) {
				const $s = $('#select_site');
				$s.empty().append('<option value=""></option>');
				$.each(sites, (i, s) => $s.append('<option value="' + s.id + '">' + s.name + '</option>'));
				$s.val(site).trigger('change.select2');
				loadAll();
			});
		});
	}

	function currentFilters() {
		return {
			startDate: isoFromFr($('#input_start_date').val()),
			endDate: isoFromFr($('#input_end_date').val()),
			region: $('#select_region').val() || null,
			district: $('#select_district').val() || null,
			site: $('#select_site').val() || null,
			lab: $('#select_lab').val() || null
		};
	}

	function loadAll() {
		loadFunnelAndKpis();
		loadCoverage();
		loadTypeBreakdown();
		loadChartStatusByType();
		loadChartSeries();
		loadChartDurations();
		loadDrillRegions();
		loadTopPerformers();
	}

	function loadFunnelAndKpis() {
		const f = currentFilters();
		$.when(
			$.get('dashboard/data/funnel', f),
			$.get('dashboard/data/funnel-previous', Object.assign({}, f, { period: window._period || '' }))
		).done(function(currResp, prevResp) {
			const curr = currResp[0] || {};
			const prev = prevResp[0] || {};
			renderPeriodBanner(prev);
			updateKpis(curr, prev);
		}).fail(function() {
			$('[data-kpi]').text('—');
		});
	}

	function updateKpis(curr, prev) {
		const map = {
			total: 'total', in_transit: 'in_transit', at_hub: 'at_hub', at_lab: 'at_lab',
			analysed: 'analysed', result_collected: 'result_collected',
			delivered: 'delivered', tat_avg_days: 'tat_avg_days',
			non_conform: 'non_conform', failed: 'failed'
		};
		const prevRange = (prev.prev_start && prev.prev_end)
			? 'Période précédente : du ' + frFromIso(prev.prev_start) + ' au ' + frFromIso(prev.prev_end)
			: '';
		Object.entries(map).forEach(function(p) {
			const v = num(curr[p[1]]);
			$('[data-kpi="' + p[0] + '"]').text(formatNumber(v));
			renderTrend('[data-trend="' + p[0] + '"]', v, num(prev[p[1]]), prevRange,
				p[0] === 'tat_avg_days' ? ' j' : '', LOWER_IS_BETTER.includes(p[0]));
		});
	}

	// Tendance : variation en % puis valeur de la période précédente (obs. 1.5 :
	// plus de « vs préc. », la valeur précédente est affichée). Les dates de la
	// période précédente sont en info-bulle.
	// Indicateurs pour lesquels une hausse est une mauvaise nouvelle : la
	// flèche suit la valeur, la couleur suit le sens (hausse = rouge).
	const LOWER_IS_BETTER = ['non_conform', 'failed', 'tat_avg_days'];

	function renderTrend(selector, curr, prev, prevRange, unit, lowerIsBetter) {
		const good = lowerIsBetter ? 'kpi-trend-down' : 'kpi-trend-up';
		const bad = lowerIsBetter ? 'kpi-trend-up' : 'kpi-trend-down';
		const $el = $(selector);
		const prevTxt = ' <span class="kpi-trend-prev">· préc. ' + formatNumber(prev) + (unit || '') + '</span>';
		// Rien à comparer : pas de flèche ni de « nouveau », seulement la valeur.
		if (prev === 0) {
			$el.html(curr === 0 ? '' : '<span class="kpi-trend-prev">préc. 0' + (unit || '') + '</span>');
			return;
		}
		const diff = curr - prev;
		const pct = Math.round(100 * diff / prev);
		// Au-delà de +1000 %, un facteur se lit mieux (« ×297 » plutôt que « +29 620 % »).
		const ratio = curr / prev;
		const upTxt = pct >= 1000
			? '×' + (ratio < 10 ? ratio.toFixed(1).replace('.', ',') : Math.round(ratio))
			: '+' + pct + '%';
		if (Math.abs(pct) < 1) {
			$el.html('<span class="kpi-trend-flat"><i class="fas fa-minus"></i> stable</span>' + prevTxt);
		} else if (diff > 0) {
			$el.html('<span class="' + good + '"><i class="fas fa-arrow-up"></i> ' + upTxt + '</span>' + prevTxt);
		} else {
			$el.html('<span class="' + bad + '"><i class="fas fa-arrow-down"></i> ' + pct + '%</span>' + prevTxt);
		}
	}

	function loadCoverage() {
		const f = currentFilters();
		$.get('dashboard/data/coverage', f, function(c) {
			['active_sites','total_sites','active_districts','total_districts',
			 'active_regions','total_regions','active_conveyors','active_labs','total_labs',
			 'total_distance_km','avg_distance_per_sample_km',
			 'samples_without_mileage','rejected_mileage_readings','trips'].forEach(function(k) {
				$('[data-cov="' + k + '"]').text(formatNumber(c[k]));
			});
			// Part de saisie manquante : on ne montre l'avertissement que s'il
			// y a effectivement des echantillons sans kilometrage.
			$('[data-cov-warn="mileage"]').toggle(num(c.samples_without_mileage) > 0 || num(c.rejected_mileage_readings) > 0);
			$('[data-cov-warn="rejected"]').toggle(num(c.rejected_mileage_readings) > 0);
			setBar('sites', c.active_sites, c.total_sites);
			setBar('districts', c.active_districts, c.total_districts);
			setBar('regions', c.active_regions, c.total_regions);
			setBar('labs', c.active_labs, c.total_labs);
		});
	}

	function setBar(key, active, total) {
		const pct = num(total) > 0 ? Math.round(100 * num(active) / num(total)) : 0;
		$('[data-cov-bar="' + key + '"]').css('width', pct + '%').attr('title', pct + '%');
	}

	function loadTypeBreakdown() {
		const f = currentFilters();
		$.get('dashboard/data/type-breakdown', f, function(rows) {
			const data = (rows || []).map(r => ({ name: r.sample_type || '—', y: num(r.total), color: typeColor(r.sample_type) }))
				.sort((x, y) => y.y - x.y);
			Highcharts.chart('type-breakdown-chart', {
				chart: { type: 'bar', backgroundColor: 'transparent', spacing: [0, 0, 0, 0] },
				title: { text: null },
				credits: { enabled: false },
				exporting: { enabled: false },
				legend: { enabled: false },
				xAxis: { type: 'category', labels: { style: { fontSize: '10px' } }, lineWidth: 0, tickWidth: 0 },
				yAxis: { title: { text: null }, gridLineWidth: 0, labels: { enabled: false }, min: 0 },
				plotOptions: {
					// Chaque barre prend la couleur de son type (cf. TYPE_COLORS).
					bar: {
						maxPointWidth: 16,
						borderWidth: 0,
						dataLabels: { enabled: true, format: '{point.y}', style: { fontSize: '10px', fontWeight: 600, color: '#374151', textOutline: 'none' } },
						borderRadius: 3
					}
				},
				series: [{ name: 'Échantillons', data: data }]
			});
		});
	}

	// Couleurs des étapes du parcours : échelle ordinale d'une teinte, plus
	// foncée avec l'avancement, bleu clair → violet foncé, écarts de clarté
	// maximaux (validée : dataviz validate_palette --ordinal).
	// La même étape a la même couleur dans le bandeau et dans tous les graphiques.
	const STAGE = {
		collected:       '#7aacf6',
		deposited:       '#5481ed',
		analysed:        '#4353d6',
		resultCollected: '#3b28a7',
		delivered:       '#2b086c'
	};
	const STATUS_CRITICAL = '#dc2626'; // réservé aux rejets / non-conformités

	// Couleur propre à chaque type d'échantillon, identique sur le web et le
	// mobile (lib/widgets/sample_list_item.dart). Palette catégorielle validée
	// (dataviz validate_palette, ordre adjacent), attribuée par fréquence. Le
	// nom du type est toujours écrit à côté : la couleur n'est jamais seule.
	const TYPE_COLORS = {
		CV: '#2a78d6', EID: '#eb6834', TB: '#1baf7a', BI: '#eda100',
		BS: '#e87ba4', HPV: '#008300', PREP: '#4a3aa7', IVSA: '#e34948'
	};
	function typeColor(t) {
		return TYPE_COLORS[String(t || '').trim().toUpperCase()] || '#94a3b8'; // autre : gris
	}
	function typeDot(t) {
		return '<span class="type-dot" style="background:' + typeColor(t) + '"></span>' + escapeHtml(t);
	}

	function loadChartStatusByType() {
		const f = currentFilters();
		$.get('dashboard/sample_status_by_sample_type', f, function(data) {
			window._statusByType = data || {};
			renderStatusByType();
		});
	}

	// Parcours par type : les types sans aucun échantillon sont masqués ; le
	// mode « % des collectés » rend lisibles les petits types face à CV.
	function renderStatusByType() {
		const data = window._statusByType || {};
		const pct = window._statusMode === 'pct';
		const cats = data.categories || [];
		const collected = data.collected || [];
		const keep = cats.map((c, i) => i).filter(i => num(collected[i]) > 0
			|| ['delivered', 'analysisDone', 'resultCollected', 'resultOnSite', 'nonConform']
				.some(k => num((data[k] || [])[i]) > 0));
		const pick = arr => keep.map(i => {
			const v = num((arr || [])[i]);
			if (!pct) return v;
			const base = num(collected[i]);
			return base > 0 ? Math.round(1000 * v / base) / 10 : null;
		});
		Highcharts.chart('chart_status_by_type', {
			chart: { type: 'column' },
			title: { text: null },
			credits: { enabled: false },
			exporting: { chartOptions: { title: { text: "Parcours par type d'échantillon" } } },
			xAxis: { categories: keep.map(i => cats[i]), crosshair: { color: 'rgba(99,102,241,.12)' },
				labels: { useHTML: true, formatter: function() { return typeDot(this.value); } } },
			yAxis: { title: { text: pct ? '% des collectés' : "Nombre d'échantillons" }, allowDecimals: pct, min: 0,
				labels: { format: pct ? '{value} %' : '{value}' } },
			legend: { enabled: true },
			plotOptions: { column: { maxPointWidth: 24, borderRadius: 3, borderWidth: 0, groupPadding: 0.12, pointPadding: 0.06 } },
			tooltip: { shared: true, valueSuffix: pct ? ' %' : '',
				pointFormat: '<span style="color:{point.color}">\u25CF</span> {series.name} : <b>{point.y}</b><br/>' },
			series: [
				{ name: 'Collectés', data: pick(data.collected), color: STAGE.collected },
				{ name: 'Déposés', data: pick(data.delivered), color: STAGE.deposited },
				{ name: 'Analysés', data: pick(data.analysisDone), color: STAGE.analysed },
				{ name: 'Résultats collectés', data: pick(data.resultCollected), color: STAGE.resultCollected },
				{ name: 'Résultats livrés', data: pick(data.resultOnSite), color: STAGE.delivered },
				{ name: 'Non-conformités', data: pick(data.nonConform), color: STATUS_CRITICAL }
			]
		});
	}

	$(document).on('click', '[data-status-mode]', function() {
		window._statusMode = $(this).data('status-mode');
		$('[data-status-mode]').removeClass('active');
		$(this).addClass('active');
		renderStatusByType();
	});

	function loadChartSeries() {
		const f = currentFilters();
		$.get('dashboard/data/series', f, function(res) {
			window._lastSeriesData = res;
			renderTimeSeries(res, $('#granularitySelect').val() || 'day');
		});
	}

	function renderTimeSeries(res, granularity) {
		Highcharts.chart('chart_series', {
			chart: { type: 'line' },
			title: { text: null },
			credits: { enabled: false },
			exporting: { chartOptions: { title: { text: "Évolution dans le temps (granularité : " + granularity + ")" } } },
			xAxis: { type: 'datetime', crosshair: { color: '#a5b4fc', width: 1, dashStyle: 'Solid' } },
			yAxis: { title: { text: 'Nombre' }, allowDecimals: false, min: 0 },
			legend: { enabled: true },
			tooltip: { shared: true, xDateFormat: '%d/%m/%Y',
				pointFormat: '<span style="color:{point.color}">\u25CF</span> {series.name} : <b>{point.y}</b><br/>' },
			plotOptions: { line: { lineWidth: 2.5, marker: { enabled: false, radius: 5 }, states: { hover: { lineWidthPlus: 1 } } } },
			series: [
				{ name: 'Collectés', data: bucketize(res.collected, granularity), color: STAGE.collected },
				{ name: 'Déposés', data: bucketize(res.deposited, granularity), color: STAGE.deposited },
				{ name: 'Analysés', data: bucketize(res.analysed, granularity), color: STAGE.analysed },
				{ name: 'Livrés', data: bucketize(res.delivered, granularity), color: STAGE.delivered }
			]
		});
	}

	function loadChartDurations() {
		const f = currentFilters();
		$.get('dashboard/data/step-durations', f, function(rows) {
			const steps = [
				'collecte→dépôt',
				'dépôt→réception',
				'réception→analyse',
				'analyse→résultat prêt',
				'résultat prêt→collecte résultat',
				'collecte résultat→dépôt résultat'
			];
			const byType = {};
			(rows || []).forEach(function(r) {
				if (!byType[r.sampleType]) byType[r.sampleType] = {};
				byType[r.sampleType][r.step] = num(r.medianDays);
			});
			renderDurationsHeatmap(steps, byType);
		});
	}

	// Étapes du tableau des durées : libellé lisible et dates utilisées.
	const STEP_DEFS = {
		'collecte→dépôt': { label: 'Collecte → dépôt',
			tip: "De la collecte (prélèvement) au dépôt au laboratoire ou au labo relais." },
		'dépôt→réception': { label: 'Dépôt → acceptation',
			tip: "Du dépôt à l'acceptation de l'échantillon par le laboratoire." },
		'réception→analyse': { label: 'Acceptation → fin d\'analyse',
			tip: "De l'acceptation par le laboratoire à la fin de l'analyse." },
		'analyse→résultat prêt': { label: 'Fin d\'analyse → validation',
			tip: "De la fin de l'analyse à la validation biologique du résultat (résultat prêt)." },
		'résultat prêt→collecte résultat': { label: 'Validation → récupération',
			tip: "De la validation du résultat à sa récupération au laboratoire par un convoyeur." },
		'collecte résultat→dépôt résultat': { label: 'Récupération → remise au site',
			tip: "De la récupération du résultat à sa remise au site de collecte." }
	};

	// Ordre fixe des types (les plus fréquents d'abord) ; les autres suivent.
	const TYPE_ORDER = ['CV', 'EID', 'TB', 'BI', 'BS', 'HPV', 'PrEP', 'IVSA'];

	// Carte de chaleur étape × type : une seule teinte chaude, plus foncée
	// quand l'étape est plus longue (échelle séquentielle sur le maximum).
	function renderDurationsHeatmap(steps, byType) {
		const types = Object.keys(byType).sort((x, y) => {
			const ix = TYPE_ORDER.indexOf(x), iy = TYPE_ORDER.indexOf(y);
			return (ix < 0 ? 99 : ix) - (iy < 0 ? 99 : iy) || x.localeCompare(y);
		});
		const $w = $('#durations_heatmap');
		if (!types.length) {
			$w.html('<div class="empty-state">Aucune durée calculable sur la période.</div>');
			return;
		}
		let max = 0;
		types.forEach(t => steps.forEach(st => { max = Math.max(max, num(byType[t][st])); }));
		const lo = [255, 241, 230], hi = [194, 65, 12]; // #fff1e6 → #c2410c
		const cell = v => {
			if (v === undefined || v === null) return '<td class="empty">—</td>';
			const t = max > 0 ? v / max : 0;
			const rgb = lo.map((c, k) => Math.round(c + (hi[k] - c) * t));
			const ink = t > 0.5 ? '#ffffff' : '#1f2937'; // texte lisible selon la luminance
			return '<td style="background:rgb(' + rgb.join(',') + ');color:' + ink + '" title="' + v + ' j (médiane)">'
				+ formatNumber(v) + ' j</td>';
		};
		let html = '<table class="heatmap"><thead><tr><th></th>'
			+ types.map(t => '<th>' + typeDot(t) + '</th>').join('') + '</tr></thead><tbody>';
		steps.forEach(st => {
			const d = STEP_DEFS[st] || {};
			html += '<tr><th>' + escapeHtml(d.label || st)
				+ (d.tip ? '<span class="help-tip" data-tip="' + escapeHtml(d.tip) + '" tabindex="0">i</span>' : '') + '</th>'
				+ types.map(t => cell(byType[t][st])).join('') + '</tr>';
		});
		html += '</tbody></table>'
			+ '<div class="heatmap-legend"><span>0 j</span><span class="scale"></span><span>' + formatNumber(max) + ' j</span></div>';
		$w.html(html);
	}

	// Niveau courant du tableau de repartition : 'region' (defaut, avec
	// cascade depliable), 'district' ou 'site' (vue a plat, sans cascade).
	window._drillLevel = 'region';

	function loadDrillRegions() {
		const f = currentFilters();
		const level = window._drillLevel || 'region';
		const url = level === 'region' ? 'dashboard/data/by-region'
			: (level === 'district' ? 'dashboard/data/by-district' : 'dashboard/data/by-site');
		$.get(url, f, function(rows) {
			window._drillRegions = rows || [];
			window._drillPage = 1;
			renderDrillRegions();
		});
	}

	function renderDrillRegions() {
		const showEmpty = $('#showEmptyZones').is(':checked');
		let rows = window._drillRegions || [];
		const totalRowsRaw = rows.length;
		if (!showEmpty) rows = rows.filter(r => num(r.total) > 0);
		const $body = $('#drillTableBody').empty();
		if (!rows.length) {
			$('#drillPager').hide();
			if (totalRowsRaw === 0) {
				$body.append('<tr><td colspan="10" class="empty-state">Aucune donnée pour la période sélectionnée.</td></tr>');
			} else {
				$body.append('<tr><td colspan="10" class="empty-state">Aucune zone avec activité sur la période. '
					+ '<a href="#" id="lnkShowEmpty">Afficher toutes les zones</a></td></tr>');
				$('#lnkShowEmpty').on('click', function(e) {
					e.preventDefault();
					$('#showEmptyZones').prop('checked', true).trigger('change');
				});
			}
			return;
		}
		const lvl = window._drillLevel || 'region';
		window._drillMax = Math.max.apply(null, rows.map(r => num(r.total)).concat([0]));
		// Pagination côté page (données déjà chargées). Les sous-niveaux dépliés
		// s'insèrent sous leur ligne parente, donc restent sur la même page.
		annotateRank(rows);
		const sorted = sortDrillRows(rows, lvl);
		const size = window._drillPageSize;
		const pages = Math.max(1, Math.ceil(sorted.length / size));
		window._drillPage = Math.min(Math.max(1, window._drillPage), pages);
		const from = (window._drillPage - 1) * size;
		sorted.slice(from, from + size).forEach(function(r) { $body.append(renderDrillRow(lvl, r, 0)); });
		renderSortIndicators();
		renderDrillPager(sorted.length, from, Math.min(from + size, sorted.length), pages);
	}

	// --- Pagination du tableau de répartition ---
	window._drillPage = 1;
	window._drillPageSize = 25;

	function renderDrillPager(total, from, to, pages) {
		const $p = $('#drillPager');
		// Inutile d'afficher la pagination quand tout tient sur une page.
		if (total <= 25 && window._drillPageSize === 25) { $p.hide(); return; }
		$p.css('display', 'flex');
		$p.find('[data-pager-info]').text((from + 1) + '–' + to + ' sur ' + formatNumber(total));
		$p.find('[data-pager-page]').text(window._drillPage + ' / ' + pages);
		$p.find('[data-page-nav="-1"]').prop('disabled', window._drillPage <= 1);
		$p.find('[data-page-nav="1"]').prop('disabled', window._drillPage >= pages);
	}

	$(document).on('click', '[data-page-nav]', function() {
		window._drillPage += num($(this).data('page-nav'));
		renderDrillRegions();
		document.getElementById('drillTable').scrollIntoView({ behavior: 'smooth', block: 'start' });
	});
	$(document).on('change', '#drillPageSize', function() {
		window._drillPageSize = num(this.value) || 25;
		window._drillPage = 1;
		renderDrillRegions();
	});

	// --- Tri du tableau de répartition (clic sur un en-tête) ---
	// En vue Régions, trier referme les régions dépliées (les lignes sont
	// reconstruites) ; les sous-niveaux dépliés ensuite suivent le même tri.
	window._drillSort = { key: 'id', dir: 'asc' }; // défaut : ordre des ID (obs. 1.10)

	function drillId(r, lvl) { return lvl === 'region' ? r.region_id : (lvl === 'district' ? r.district_id : r.site_id); }
	function drillName(r, lvl) { return lvl === 'region' ? r.region : (lvl === 'district' ? r.district : r.site); }

	function sortDrillRows(rows, lvl) {
		const sort = window._drillSort;
		const val = r => sort.key === 'id' ? num(drillId(r, lvl))
			: sort.key === 'name' ? String(drillName(r, lvl) || '')
			: num(r[sort.key]);
		const dir = sort.dir === 'asc' ? 1 : -1;
		return rows.slice().sort(function(a, b) {
			const va = val(a), vb = val(b);
			const c = typeof va === 'string' ? va.localeCompare(vb, 'fr', { sensitivity: 'base' }) : va - vb;
			return c * dir;
		});
	}

	function renderSortIndicators() {
		$('#drillTable th.sortable').each(function() {
			const on = $(this).data('sort') === window._drillSort.key;
			$(this).toggleClass('sorted', on).attr('aria-sort',
				on ? (window._drillSort.dir === 'asc' ? 'ascending' : 'descending') : 'none');
			$(this).find('.sort-arrow').remove();
			$(this).append('<i class="fas sort-arrow ' + (on
				? (window._drillSort.dir === 'asc' ? 'fa-sort-up' : 'fa-sort-down') : 'fa-sort') + '"></i>');
		});
	}

	$(document).on('click', '#drillTable th.sortable', function() {
		const key = $(this).data('sort');
		const s = window._drillSort;
		// Nouveau critère : décroissant pour les nombres, alphabétique pour le nom.
		window._drillSort = s.key === key
			? { key: key, dir: s.dir === 'asc' ? 'desc' : 'asc' }
			: { key: key, dir: key === 'name' || key === 'id' ? 'asc' : 'desc' };
		window._drillPage = 1;
		renderDrillRegions();
	});

	// --- Export CSV du niveau affiché (Excel : séparateur « ; », BOM UTF-8) ---
	$(document).on('click', '#btnExportDrill', function() {
		const lvl = window._drillLevel || 'region';
		const showEmpty = $('#showEmptyZones').is(':checked');
		let rows = window._drillRegions || [];
		if (!showEmpty) rows = rows.filter(r => num(r.total) > 0);
		annotateRank(rows);
		rows = sortDrillRows(rows, lvl);
		const cols = ['ID'].concat(lvl === 'region' ? ['Région'] : lvl === 'district' ? ['Région', 'District'] : ['Région', 'District', 'Site'])
			.concat(['Rang', 'Total', 'Écart à la moyenne (%)', 'En transit', 'Livrés', 'Non-conformités', "Échecs d'analyse", 'TAT moyen (j)']);
		const cell = v => {
			let t = v == null ? '' : String(v);
			// Un tableur interprète comme formule un texte commençant par
			// = + - @ (injection de formule) : on le neutralise par une apostrophe.
			if (typeof v === 'string' && /^[=+\-@\t\r]/.test(t)) t = "'" + t;
			return /[;"\n]/.test(t) ? '"' + t.replace(/"/g, '""') + '"' : t;
		};
		const dec = v => (v == null || v === '') ? '' : String(num(v)).replace('.', ',');
		// En-tête (cahier V.5) : export horodaté, avec le périmètre et la période,
		// pour qu'un fichier renommé ou transmis reste interprétable.
		const f0 = currentFilters();
		const selText = sel => { const t = $(sel + ' option:selected').text().trim(); return $(sel).val() ? t : ''; };
		const perimetre = [selText('#select_region'), selText('#select_district'), selText('#select_site')]
			.filter(Boolean).join(' › ') || 'Tout le périmètre de l\'utilisateur';
		const labo = selText('#select_lab');
		const now = new Date();
		const pad = n => String(n).padStart(2, '0');
		const header = [
			cell('LSTracker — Répartition par ' + { region: 'région sanitaire', district: 'district sanitaire', site: 'site' }[lvl]),
			cell('Période : ' + (f0.startDate ? frFromIso(f0.startDate) : 'début') + ' au ' + (f0.endDate ? frFromIso(f0.endDate) : "aujourd'hui")),
			cell('Périmètre : ' + perimetre + (labo ? ' · Laboratoire : ' + labo : '')),
			cell('Exporté le ' + pad(now.getDate()) + '/' + pad(now.getMonth() + 1) + '/' + now.getFullYear()
				+ ' à ' + pad(now.getHours()) + ':' + pad(now.getMinutes())),
			''
		];
		const lines = header.concat([cols.join(';')]).concat(rows.map(function(r) {
			const geo = lvl === 'region' ? [r.region] : lvl === 'district' ? [r.region, r.district] : [r.region, r.district, r.site];
			return [drillId(r, lvl)].concat(geo)
				.concat([r._rank, num(r.total), r._gap == null ? '' : r._gap, num(r.in_transit), num(r.delivered), num(r.non_conform), num(r.failed), num(r.total) > 0 ? dec(r.tat_avg_days) : ''])
				.map(cell).join(';');
		}));
		const f = currentFilters();
		const name = 'repartition_' + { region: 'regions', district: 'districts', site: 'sites' }[lvl]
			+ (f.startDate ? '_' + f.startDate : '') + (f.endDate ? '_' + f.endDate : '') + '.csv';
		const blob = new Blob(['\ufeff' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' });
		const a = document.createElement('a');
		a.href = URL.createObjectURL(blob);
		a.download = name;
		document.body.appendChild(a);
		a.click();
		setTimeout(function() { URL.revokeObjectURL(a.href); a.remove(); }, 0);
	});

	function renderDrillRow(level, r, indent) {
		// Les vues a plat renvoient aussi les libelles parents (region du
		// district, region+district du site) : on resout donc par NIVEAU et
		// non par premier champ non vide, sinon un district afficherait le
		// nom de sa region.
		const id = level === 'region' ? r.region_id
			: (level === 'district' ? r.district_id : r.site_id);
		const name = level === 'region' ? r.region
			: (level === 'district' ? r.district : r.site);
		// Contexte parent, affiche en gris a cote du nom en vue a plat.
		const parent = level === 'district' ? r.region
			: (level === 'site' ? [r.region, r.district].filter(Boolean).join(' › ') : null);
		const cls = 'drill-' + level;
		// La cascade n'existe qu'en vue "Régions" : les vues à plat
		// (Districts / Sites) listent deja tout le niveau demande.
		const flat = (window._drillLevel || 'region') !== 'region';
		const expander = (level === 'site' || flat)
			? '<span class="expander placeholder">·</span>'
			: '<span class="expander" data-toggle="' + level + '" data-id="' + id + '" onclick="toggleDrill(this)">▶</span>';
		const indentCls = indent === 1 ? 'indent-1' : (indent === 2 ? 'indent-2' : '');
		return '<tr class="' + cls + '" data-row-id="' + level + '-' + id + '">'
			+ '<td class="num drill-id">' + (id != null ? id : '—') + '</td>'
			+ '<td><span class="' + indentCls + '">' + expander + ' ' + escapeHtml(name || '—')
			+   (parent && (window._drillLevel || 'region') !== 'region'
					? ' <span class="drill-parent">' + escapeHtml(parent) + '</span>' : '')
			+ '</span></td>'
			+ '<td class="num"><span class="rank-chip">' + (r._rank || '—') + '</span></td>'
			+ totalCell(r.total)
			+ '<td class="num">' + gapCell(r._gap) + '</td>'
			+ '<td class="num">' + formatNumber(r.in_transit) + '</td>'
			+ '<td class="num">' + formatNumber(r.delivered) + '</td>'
			+ '<td class="num">' + formatNumber(r.non_conform) + '</td>'
			+ '<td class="num">' + formatNumber(r.failed) + '</td>'
			+ '<td class="num">' + renderTat(r.tat_avg_days, r.total) + '</td>'
			+ '</tr>';
	}

	// Selecteur de niveau : bascule entre Regions (cascade) et les vues a
	// plat Districts / Sites.
	$(document).on('click', '.drill-level button', function() {
		const lvl = $(this).data('level');
		if (lvl === window._drillLevel) return;
		window._drillLevel = lvl;
		$('.drill-level button').removeClass('active');
		$(this).addClass('active');
		$('[data-drill-hint]').toggle(lvl === 'region');
		// Titre et en-tête suivent le niveau (obs. 1.12 : tableau des districts
		// sanitaires). En mode Régions, la cascade peut afficher les trois niveaux.
		const labels = {
			region: ['Répartition par région sanitaire', 'Région / District / Site'],
			district: ['Répartition par district sanitaire', 'District (région)'],
			site: ['Répartition par site', 'Site (région › district)']
		}[lvl];
		$('[data-drill-title]').text(labels[0]);
		$('[data-drill-col]').text(labels[1]);
		$('#drillTableBody').html('<tr><td colspan="10" class="empty-state">Chargement...</td></tr>');
		loadDrillRegions();
	});

	// Classement (cahier VI.2) : rang par volume et écart à la moyenne, calculés
	// parmi les lignes d'un même niveau (ou les lignes sœurs d'un dépliage).
	function annotateRank(rows) {
		const byTotal = rows.slice().sort((a, b) => num(b.total) - num(a.total));
		const mean = rows.length ? rows.reduce((acc, r) => acc + num(r.total), 0) / rows.length : 0;
		let rank = 0, prev = null;
		byTotal.forEach(function(r, i) {
			if (num(r.total) !== prev) { rank = i + 1; prev = num(r.total); } // ex æquo : même rang
			r._rank = rank;
			r._gap = mean > 0 ? Math.round(100 * (num(r.total) - mean) / mean) : null;
		});
	}

	function gapCell(gap) {
		if (gap == null) return '<span class="text-muted-sm">—</span>';
		const cls = gap > 0 ? 'gap-up' : (gap < 0 ? 'gap-down' : 'gap-flat');
		return '<span class="' + cls + '">' + (gap > 0 ? '+' : '') + gap + ' %</span>';
	}

	// Barre discrète derrière le total, relative au plus grand total du niveau
	// affiché (les sous-niveaux dépliés utilisent la même échelle).
	function totalCell(total) {
		const max = window._drillMax || 0;
		const pct = max > 0 ? Math.min(100, Math.round(100 * num(total) / max)) : 0;
		return '<td class="num total-cell" style="background:linear-gradient(to left, rgba(99,102,241,.13) '
			+ pct + '%, transparent ' + pct + '%)">' + formatNumber(total) + '</td>';
	}

	function renderTat(days, total) {
		if (!total || num(total) === 0) return '<span class="tat-pill tat-na">—</span>';
		const d = num(days);
		const cls = d < 3 ? 'tat-green' : (d < 7 ? 'tat-orange' : 'tat-red');
		return '<span class="tat-pill ' + cls + '">' + d + ' j</span>';
	}

	function toggleDrill(btn) {
		const $btn = $(btn);
		const level = $btn.data('toggle');
		const id = $btn.data('id');
		const $parent = $btn.closest('tr');
		const rowMarker = level + '-' + id;
		if ($btn.text() === '▼') {
			$btn.text('▶');
			$('tr[data-parent="' + rowMarker + '"]').remove();
			$('tr[data-ancestor*="' + rowMarker + '"]').remove();
			return;
		}
		// Filtres de l'écran + parent déplié (déplier = filtrer sur le parent).
		const f = currentFilters();
		const url = level === 'region' ? 'dashboard/data/by-district' : 'dashboard/data/by-site';
		const params = Object.assign({}, f, level === 'region' ? { region: id } : { district: id });
		$.get(url, params, function(rows) {
			$btn.text('▼');
			const showEmpty = $('#showEmptyZones').is(':checked');
			const totalRowsRaw = rows.length;
			let filtered = rows;
			if (!showEmpty) filtered = filtered.filter(r => num(r.total) > 0);
			if (!filtered.length) {
				const msg = totalRowsRaw === 0 ? 'Aucun élément'
					: 'Aucun élément avec activité (' + totalRowsRaw + ' masqué' + (totalRowsRaw > 1 ? 's' : '') + ')';
				const $empty = $('<tr><td colspan="10" class="text-muted-sm" style="padding:.4rem 1rem;"></td></tr>')
					.attr('data-parent', rowMarker);
				$empty.find('td').text(msg);
				$parent.after($empty);
				return;
			}
			const childLevel = level === 'region' ? 'district' : 'site';
			const indent = level === 'region' ? 1 : 2;
			annotateRank(filtered);
			sortDrillRows(filtered, childLevel).reverse().forEach(function(r) {
				const $row = $(renderDrillRow(childLevel, r, indent))
					.attr('data-parent', rowMarker)
					.attr('data-ancestor', rowMarker);
				$parent.after($row);
			});
		});
	}

	function loadTopPerformers() {
		const f = currentFilters();
		$.get('dashboard/data/top-performers', Object.assign({}, f, { limit: 5 }), function(resp) {
			renderRanking('#topRejectionSites', resp.rejection_sites, function(r) {
				return {
					name: r.site || '—',
					sub: (r.district || '') + ' · ' + (r.region || '') + (r.by_type ? ' · ' + r.by_type : ''),
					metric: r.non_conform_rate + '% (' + r.non_conform + '/' + r.total + ')',
					value: r.non_conform_rate
				};
			});
			renderRanking('#slowestLabs', resp.slowest_labs, function(r) {
				return {
					name: r.lab || '—',
					sub: 'TAT labo médian (dépôt → validation) · ' + formatNumber(r.total) + ' échantillons',
					metric: r.avg_tat_days + ' j',
					value: r.avg_tat_days
				};
			});
			renderRanking('#topConveyors', resp.top_conveyors, function(r) {
				const fullName = ((r.first_name || '') + ' ' + (r.last_name || '')).trim() || r.login || '—';
				return {
					name: fullName,
					sub: formatNumber(r.deposits) + ' dépôts'
						+ (r.median_transport_days != null ? ' · acheminement ' + formatDuration(r.median_transport_days) : '')
						+ (num(r.non_conform) > 0 ? ' · ' + r.non_conform_rate + '% non conf.' : ''),
					metric: formatNumber(r.samples_handled) + ' coll.',
					value: r.samples_handled
				};
			});
		});
	}

	// Classement : rang, libellé, valeur, et une barre proportionnelle au
	// premier du classement pour lire les écarts d'un coup d'œil. Les mappers
	// renvoient du texte brut : tout est échappé ici, au rendu.
	function renderRanking(selector, rows, mapper) {
		const $el = $(selector).empty();
		if (!rows || !rows.length) {
			$el.append('<li class="text-muted-sm" style="display:block">Aucune donnée pour la période</li>');
			return;
		}
		const mapped = rows.map(mapper);
		const max = Math.max.apply(null, mapped.map(m => num(m.value)).concat([0]));
		mapped.forEach(function(m, i) {
			const w = max > 0 ? Math.round(100 * num(m.value) / max) : 0;
			$el.append('<li>'
				+ '<span class="rank">' + (i + 1) + '</span>'
				+ '<div><div class="name">' + escapeHtml(m.name) + '</div><div class="sub">' + escapeHtml(m.sub) + '</div></div>'
				+ '<div class="metric">' + escapeHtml(m.metric) + '</div>'
				+ '<div class="rank-bar"><span style="width:' + w + '%"></span></div>'
				+ '</li>');
		});
	}

	// ===== Helpers =====
	function isoFromFr(s) {
		if (!s) return null;
		const m = s.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
		return m ? (m[3] + '-' + m[2] + '-' + m[1]) : null;
	}
	function num(v) {
		if (v === null || v === undefined) return 0;
		if (typeof v === 'number') return v;
		const n = Number(v);
		return isNaN(n) ? 0 : n;
	}
	// Durée médiane en jours → « 2 h » sous 24 h, « 1,5 j » au-delà.
	function formatDuration(days) {
		const d = num(days);
		if (d < 1) return Math.max(1, Math.round(d * 24)) + ' h';
		return String(Math.round(d * 10) / 10).replace('.', ',') + ' j';
	}
	function formatNumber(v) {
		const n = num(v);
		return n.toLocaleString('fr-FR');
	}
	function escapeHtml(str) {
		if (str == null) return '';
		return String(str).replace(/[&<>"']/g, function(c) {
			return ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c];
		});
	}
	function toUTC(ts) {
		if (typeof ts !== 'string') return ts;
		const parts = ts.split('-').map(Number);
		return Date.UTC(parts[0], parts[1] - 1, parts[2]);
	}
	function startOfWeekUTC(ts) {
		const d = new Date(ts);
		const dow = (d.getUTCDay() + 6) % 7;
		d.setUTCDate(d.getUTCDate() - dow);
		d.setUTCHours(0, 0, 0, 0);
		return d.getTime();
	}
	function startOfMonthUTC(ts) {
		const d = new Date(ts);
		return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1);
	}
	function bucketize(data, granularity) {
		const map = new Map();
		(data || []).forEach(function(pt) {
			const ts = toUTC(pt.day);
			let key;
			if (granularity === 'week') key = startOfWeekUTC(ts);
			else if (granularity === 'month') key = startOfMonthUTC(ts);
			else key = new Date(ts).setUTCHours(0, 0, 0, 0);
			map.set(key, (map.get(key) || 0) + num(pt.cnt));
		});
		return Array.from(map.entries()).sort((a, b) => a[0] - b[0]);
	}
	