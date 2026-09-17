
if (top.location == self.location) window.location = "https://overlay.miku-box.com/error/no_video";

else {

}

!function() {
	/*referans:box*/
    navigator.userAgent.match(/Android/i) || navigator.userAgent.match(/webOS/i) || navigator.userAgent.match(/iPhone/i) || navigator.userAgent.match(/iPad/i) || navigator.userAgent.match(/iPod/i) || navigator.userAgent.match(/BlackBerry/i) || navigator.userAgent.match(/Windows Phone/i) || (devtoolsDetector.addListener(function(t, e) {
        t && (document.location.href = "https://overlay.miku-box.com/error/no_video")
    }), devtoolsDetector.lanuch())
}();