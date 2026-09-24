/*
 * Actions des listes du maillage (régions, districts, sites, laboratoires,
 * circuits) : modifier, désactiver / réactiver, supprimer (cahier IV.f).
 * Les actions qui modifient passent en POST avec le jeton anti-falsification
 * (cookie XSRF-TOKEN), jamais par un simple lien.
 */
(function () {
	'use strict';

	function esc(s) {
		return String(s == null ? '' : s).replace(/[&<>"']/g, c =>
			({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
	}

	/** Boutons d'une ligne ; row.is_active absent = actif. */
	window.meshActions = function (type, row, noun) {
		const id = row.id;
		const active = row.is_active !== false;
		return '<div class="row-actions">'
			+ (active ? '' : '<span class="state-off" title="Désactivé : plus proposé à la saisie">Inactif</span>')
			+ '<a class="btn-action btn-warning-soft" title="Modifier" href="/' + type + '/update/' + id + '"><i class="fas fa-pencil-alt"></i></a>'
			+ (active
				? '<a class="btn-action js-post" title="Désactiver" href="#" data-url="/' + type + '/deactivate/' + id + '"'
					+ ' data-message="' + esc('Désactiver ' + noun + ' ? Il ne sera plus proposé à la saisie ; ses données restent consultables.') + '">'
					+ '<i class="fas fa-toggle-on"></i></a>'
				: '<a class="btn-action js-post" title="Réactiver" href="#" data-url="/' + type + '/activate/' + id + '"'
					+ ' data-message="' + esc('Réactiver ' + noun + ' ?') + '"><i class="fas fa-toggle-off"></i></a>')
			+ '<a class="btn-action btn-danger-soft js-post" title="Supprimer" href="#" data-url="/' + type + '/delete/' + id + '"'
			+ ' data-message="' + esc('Supprimer définitivement ' + noun + ' ? Impossible s\'il est déjà utilisé : il faudra alors le désactiver.') + '">'
			+ '<i class="fas fa-trash-alt"></i></a>'
			+ '</div>';
	};

	function csrfToken() {
		const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
		return m ? decodeURIComponent(m[1]) : '';
	}

	document.addEventListener('click', function (e) {
		const a = e.target.closest('a.js-post');
		if (!a) return;
		e.preventDefault();
		if (!confirm(a.dataset.message)) return;
		const form = document.createElement('form');
		form.method = 'post';
		form.action = a.dataset.url;
		const input = document.createElement('input');
		input.type = 'hidden';
		input.name = '_csrf';
		input.value = csrfToken();
		form.appendChild(input);
		document.body.appendChild(form);
		form.submit();
	});

	const style = document.createElement('style');
	style.textContent = '.row-actions .state-off{display:inline-block;font-size:.68rem;font-weight:600;'
		+ 'color:#92400e;background:#fef3c7;border-radius:4px;padding:.05rem .4rem;margin-right:.25rem;align-self:center}'
		+ '.row-actions .js-post .fa-toggle-on{color:#15803d}.row-actions .js-post .fa-toggle-off{color:#9ca3af}';
	document.head.appendChild(style);
})();
