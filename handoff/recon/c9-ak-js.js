function displayvideo(w, v) {
    if (w == 0) {
        var url = web_uri + "video/" + v + "/";
    } else {
        var url = web_uri + "video/" + v + "/";
    }
    $('.reload').attr('onclick', 'displayvideo(' + w + ',' + v + ')');
    $("#display").html('<iframe id="iframe-embed" class="embed-responsive-item lazyloaded" src="' + url + '" frameborder="0" scrolling="no" allowfullscreen></iframe></div>');
    /*
    new LazyLoad({
        elements_selector: ".lazyloaded"
    });
    */
}

$('.report__btn').on('click', function() {
    let data = $(this).attr('data-id');
    $(this).html('<i class="fa fa-flag-o"></i> กำลังตรวจสอบ..');
    $.ajax({
            url: web_uri + 'video/library/toCheck2.php',
            type: 'POST',
            dataType: 'json',
            data: {
                video_id: data
            },
        })
        .done(function(d) {
            if (d.status) {
                $('.report__btn').html('<i class="fa fa-flag-o"></i> กำลังบันทึก..');
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
                            $('.report__btn').html('<i class="fa fa-check-circle"></i> สำเร็จ');
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