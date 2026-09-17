$('#sidebarCollapse').on('click', function() {
    $('.sidebar').addClass('active')
    $('.overlay-2').addClass('active')
})

$('.menu_icon__close').on('click', function() {
    $('.sidebar').removeClass('active')
    $('.overlay-2').removeClass('active')
})

$('.overlay-2').on('click', function() {
    $('.sidebar').removeClass('active')
    $('.overlay-2').removeClass('active')
})

$('.report__btn').on('click', function() {

    let data = $(this).attr('data-id');

    $(this).html('<i class="fa fa-flag-o"></i> Checking..');

    $.ajax({
            url: web_uri + 'video/library/toCheck.php',
            type: 'POST',
            dataType: 'json',
            data: {
                video_id: data
            },
        })
        .done(function(d) {
            if (d.status) {
                $('.report__btn').html('<i class="fa fa-flag-o"></i> Saving..');
                $.ajax({
                        url: web_uri + 'video/library/toFix.php',
                        type: 'POST',
                        dataType: 'json',
                        data: {
                            video_id: d.iden,
                            fixS: d.fixS
                        },
                    })
                    .done(function(f) {
                        if (f.i) {
                            $('.report__btn').html('<i class="fa fa-check-circle"></i> Succeed');
                            setTimeout(() => {
                                $('.report__btn').fadeOut(500)
                            }, 200);
                        }
                    })
            } else {
                $('.report__btn').html('<i class="fa fa-flag-o"></i> ' + d.msg);
            }
        })
})

$('.backup-menu').on('click', function(e) {
    e.preventDefault()
    let id = $(this).attr('id')

    console.log(id)
    if (id == 'p-main') {
        $('#video-container').removeClass('embed-responsive embed-responsive-16by9')
        $('#video-container').html('<video id="display" class="display"></video>')
        setTimeout(() => {
            display.setup(video_data);
        }, 500);

    } else {
        let url = $(this).attr('data-url')
        $('#video-container').addClass('embed-responsive embed-responsive-16by9')
        $('#video-container').html('<iframe class="embed-responsive-item" src="' + url + '" allowfullscreen></iframe>')
    }

    $('.sidebar').removeClass('active')
    $('.overlay-2').removeClass('active')
})

display.setup(video_data);
document.addEventListener('contextmenu', event => event.preventDefault());