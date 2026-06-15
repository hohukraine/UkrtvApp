//$('head').append('<link rel="stylesheet" type="text/css" href="/templates/' + dle_skin + '/playlists/style.css">'); 

$('body').on('click', '.playlists-lists li:not(.voice_crating)', function() {
	var player = $(this).closest('.playlists-player'),
		id = $(this).data('id'),
		index = $(this).closest('.playlists-items').index(),
		count = $(this).closest('.playlists-lists').find('.playlists-items').length;
	
	$(this).closest('.playlists-items').find('li').removeClass('active');
	$(this).addClass('active');

	for (i=index+1;i<count;i++) {
		var _tmp = player.find('.playlists-lists .playlists-items:eq(' + i + ')');

		_tmp.find('li').removeClass('active').hide();
		_tmp.find('li[data-id^=' + id + ']').show();

		_id = _tmp.find('li:visible:eq(0)').addClass('active').data('id');

		if ( typeof _id != 'undefined' ) {
			id = _id;
			_self = _tmp;			
		}
	}
	
	player.find('.playlists-videos').find('li').removeClass('active').hide();
	player.find('.playlists-videos').find('li[data-id=' + id + ']').show();
	player.find('.playlists-videos li:visible:eq(0)').addClass('active');
    
    let iframe = player.find('.playlists-iframe iframe');
    if (iframe.length) {
        iframe.attr('src', player.find('.playlists-videos li.active').data('file') + ((typeof playlist_iframe_suffix != 'undefined') ? ((player.find('.playlists-videos li.active').data('file').indexOf('?') == -1) ? '?' : '&') + playlist_iframe_suffix : ''));
    } else {
        player.find('.playlists-iframe').html('<iframe name="playerfr" id="playerfr" loading="lazy" scrolling="no" src="' + player.find('.playlists-videos li.active').data('file') + ((typeof playlist_iframe_suffix != 'undefined') ? ((player.find('.playlists-videos li.active').data('file').indexOf('?') == -1) ? '?' : '&') + playlist_iframe_suffix : '') + '" frameborder="0" width="100%" height="100%" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen;" ></iframe>');
    }
});

var sel_player = false;
$('body').on('click', '.playlists-videos li:not(.voice_crating)', function(event) {
	if ( event.target.tagName != 'LI' ) {
		event.stopPropagation();
		return false;
	}
	


	$(this).closest('.playlists-items').find('li').removeClass('active');
	$(this).addClass('active');
    let iframeWrap = $(this).closest('.playlists-player').find('.playlists-iframe'),
        iframe = iframeWrap.find('iframe');
    
    if (iframe.length) {
        iframe.attr('src', $(this).data('file') + ((typeof playlist_iframe_suffix != 'undefined') ? (($(this).data('file').indexOf('?') == -1) ? '?' : '&') + playlist_iframe_suffix : ''));
    } else {
        $(this).closest('.playlists-player').find('.playlists-iframe').html('<iframe name="playerfr" id="playerfr" loading="lazy" scrolling="no" src="' + $(this).data('file') + ((typeof playlist_iframe_suffix != 'undefined') ? (($(this).data('file').indexOf('?') == -1) ? '?' : '&') + playlist_iframe_suffix : '') + '" frameborder="0" width="100%" height="100%" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen;" ></iframe>');
    }
    
	var news_id = $(this).closest('.playlists-ajax').data('news_id'),
		xfname = $(this).closest('.playlists-ajax').data('xfname'),
		file = $(this).closest('li').data('file'),
		fid = $(this).closest('li').data('id'),
		c = 'watched-' + news_id + '-' + xfname,
		cr = 'playlists-' + news_id + '-' + xfname + '-' + file;
			
	localStorage.setItem(c, file);
	

	
	if(sel_player) {
		sel_player = false;
		var player = $(this).closest('.playlists-player');
		
		player.find('.playlists-videos').find('li').hide();
		player.find('.playlists-videos').find('li[data-id=' + fid + ']').show();
		$('.playlists-lists li').removeClass('active');
		$('.playlists-lists').find('li[data-id=' + fid + ']').addClass('active');
	} else {

		$(this).find('.playlists-view').addClass('watched');
		localStorage.setItem(cr, '1');
		
	}	
});

// кнопки отметок для просмотренных серий
$('body').on('click', '.playlists-view', function(event) {
	var news_id = $(this).closest('.playlists-ajax').data('news_id'),
		xfname = $(this).closest('.playlists-ajax').data('xfname'),
		file = $(this).closest('li').data('file'),
		c = 'playlists-' + news_id + '-' + xfname + '-' + file;

	$(this).toggleClass('watched');
	
	if ( localStorage.getItem(c) === null ) localStorage.setItem(c, '1');
		else localStorage.removeItem(c);
});
// ### кнопки отметок для просмотренных серий


document.addEventListener('DOMContentLoaded', () => {
    const playlist = document.querySelector('.playlists-ajax');
    if (!playlist) return;
    
    const observer = new IntersectionObserver(([entry]) => {
        if (!entry.isIntersecting) return;
        
        observer.disconnect();
        
        const $el = $(playlist);
        const { xfname, news_id } = $el.data();
        
        $.ajax({
            url: '/engine/ajax/playlists.php',
            data: { news_id: news_id, xfield: xfname, time: dle_edittime },
            dataType: 'JSON',
            cache: true,
            success: function(data) {
                if ( !data.success ) {
                    console.log('Playlists', data.message);
                    return false;
                }

                $el.html(data.response);
                
                if ( $el.find('.playlists-lists li').length )
                    $el.find('.playlists-lists li:first').trigger('click');
                else
                    $el.find('.playlists-videos li:first').trigger('click');                

                // кнопки отметок для просмотренных серий
                $el.find('.playlists-videos li:not(.voice_crating)').each(function() {
                    var file = $(this).data('file'),
                        c = 'playlists-' + news_id + '-' + xfname + '-' + file,
                        d = 'watched-' + news_id + '-' + xfname,
                        fid = $(this).data('id'),
                        cp = 'watched-player-' + news_id;    

                    $(this).append('<span class="playlists-view"></span>');

                    if ( localStorage.getItem(c) !== null ) $(this).find('.playlists-view').addClass('watched');
                    
                    if ( localStorage.getItem(d) !== null && localStorage.getItem(d) === file ) {
                        sel_player = true;
                        $(this).trigger('click');
                    }                   
                });    
            },
            error: function(a, b, c) {
                console.log('Playlists', 'ERR_REQUEST_FAILED');
            }
        });
    }, { rootMargin: '50px' });
    
    observer.observe(playlist);
});