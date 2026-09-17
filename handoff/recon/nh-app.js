$( document ).ready(function(){
        $('.servers span').click(function(){
          $('.servers ul').toggleClass('active');
        });
        $('.servers ul li').click(function(){
          var embed = $(this).attr('data-id');
          $('.servers ul li').removeClass('active');
          $(this).addClass('active');

			if(embed.indexOf('htstreaming') > 1){
				$('.frame header').addClass('dl');
			}else{
				$('.frame header').removeClass('dl');
			}
          $('.frame .play').html('<div class="loader"></div>');
          $('.frame iframe').attr('src', embed);
          $('.frame iframe').on('load', function(){
            $('.frame .play, .frame .backdrop').hide();
          });
        });
        $('.frame .play').click(function(){
			var embed = $('.servers ul li').first().attr('data-id');
			if(embed.indexOf('htstreaming') > 1){
				$('.frame header').addClass('dl');
			}else{
				$('.frame header').removeClass('dl');
			}
          $('.frame .play').html('<div class="loader"></div>');
          $('.frame iframe').attr('src', embed);
		  
		  
          $('.frame iframe').on('load', function(){
            $('.frame .play, .frame .backdrop').hide();
          });
        });
      });