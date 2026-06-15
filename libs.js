$(document).ready(function() {
    var windowWidth = $(window).width();
    
	$(window).width() < 1220 && ($(".head-fixed-inner nav").append('<div class="show-menu"><i class="fa fa-bars"></i></div>'), $(".show-menu").click(function() {
			$(".hmenu").fadeToggle(200);
	}));

    if (windowWidth < 1220) {
		$("body").on("click", ".hmenu li > a", function() {
            if ($(this).parent().find('ul').length) {

                $(this).parent().find(".hidden-me").toggleClass("go");
                $(this).parent().toggleClass("menuactive");
                return false;
            }
        });

        $(".hmenu li:has(ul)").addClass("submenu");

    }
    

});


$(document).ready(function() {

	$("#show-search").click(function() {
        $("#search-wrap").slideToggle(200), $(this).toggleClass("active")
    }), 
    $("body").on({
        mouseenter: function() {
            var e = window.innerWidth,
                t = $(this).closest(".movie-item,.movie-item-table"),
                i = t.offset().left;
            e / 2 > i && t.addClass("pop-right"), t.parent().addClass("active"), t.addClass("active").find(".movie-desc").stop(!0, !0).fadeIn(200)
        },
        mouseleave: function() {
            var e = $(this).closest(".movie-item");
            e.parent().removeClass("active"), e.removeClass("active pop-right").find(".movie-desc").stop(!0, !0).fadeOut(200)
        }
    }, ".show-desc"), $("body").on("click", ".go-watch", function() {
        var e = $(this).attr("data-link");
        window.location.href = e
    }), $(".sorter").hover(function() {
        $(this).find("form").stop(!0, !0).slideToggle(200)
    }), $(window).width() > 760 && ($("#movie-right").append($("#comments")), $("#movie-left").append($("#sidebar"))), $("#show-login").click(function() {
        $("#overlay").fadeIn(200)
    }), $("#overlay").append('<i class="fa fa-times overlay-close"></i>'), $(".overlay-close").click(function() {
        $("#overlay").fadeOut(200)
    }), $("body").append('<div id="gotop" title="Наверх"></div>');
    var e = $("#gotop");
    $(window).scroll(function() {
        $(this).scrollTop() > 300 ? e.fadeIn(200) : e.fadeOut(200)
    }), e.click(function() {
        $("html, body").animate({
            scrollTop: 0
        }, "slow")
    })
}), $(function() {
    $("ul.tabs").delegate("li:not(.current)", "click", function() {
        $(this).addClass("current").siblings().removeClass("current").parents("div.players-section").find("div.box").hide().eq($(this).index()).fadeIn(400)
    })
}), $(document).ready(function() {
    if (function(e) {
            "function" == typeof define && define.amd ? define(["../../../engine/classes/js/jquery"], e) : e(jQuery)
        }(function(e) {
            function t() {
                var t = i(this);
                return isNaN(t.datetime) || e(this).text(a(t.datetime)), this
            }

            function i(t) {
                if (t = e(t), !t.data("timeago")) {
                    t.data("timeago", {
                        datetime: o.datetime(t)
                    });
                    var i = e.trim(t.text());
                    o.settings.localeTitle ? t.attr("title", t.data("timeago").datetime.toLocaleString()) : !(i.length > 0) || o.isTime(t) && t.attr("title") || t.attr("title", i)
                }
                return t.data("timeago")
            }

            function a(e) {
                return o.inWords(n(e))
            }

            function n(e) {
                return (new Date).getTime() - e.getTime()
            }
            e.timeago = function(t) {
                return a(t instanceof Date ? t : "string" == typeof t ? e.timeago.parse(t) : "number" == typeof t ? new Date(t) : e.timeago.datetime(t))
            };
            var o = e.timeago;
            e.extend(e.timeago, {
                settings: {
                    refreshMillis: 6e4,
                    allowFuture: !1,
                    localeTitle: !1,
                    strings: {
                        prefixAgo: null,
                        prefixFromNow: null,
                        suffixAgo: "ago",
                        suffixFromNow: "from now",
                        seconds: "less than a minute",
                        minute: "about a minute",
                        minutes: "%d minutes",
                        hour: "about an hour",
                        hours: "about %d hours",
                        day: "a day",
                        days: "%d days",
                        month: "about a month",
                        months: "%d months",
                        year: "about a year",
                        years: "%d years",
                        wordSeparator: " ",
                        numbers: []
                    }
                },
                inWords: function(t) {
                    function i(i, n) {
                        var o = e.isFunction(i) ? i(n, t) : i,
                            r = a.numbers && a.numbers[n] || n;
                        return o.replace(/%d/i, r)
                    }
                    var a = this.settings.strings,
                        n = a.prefixAgo,
                        o = a.suffixAgo;
                    this.settings.allowFuture && 0 > t && (n = a.prefixFromNow, o = a.suffixFromNow);
                    var r = Math.abs(t) / 1e3,
                        s = r / 60,
                        d = s / 60,
                        l = d / 24,
                        u = l / 365,
                        c = 45 > r && i(a.seconds, Math.round(r)) || 90 > r && i(a.minute, 1) || 45 > s && i(a.minutes, Math.round(s)) || 90 > s && i(a.hour, 1) || 24 > d && i(a.hours, Math.round(d)) || 42 > d && i(a.day, 1) || 30 > l && i(a.days, Math.round(l)) || 45 > l && i(a.month, 1) || 365 > l && i(a.months, Math.round(l / 30)) || 1.5 > u && i(a.year, 1) || i(a.years, Math.round(u)),
                        f = a.wordSeparator || "";
                    return void 0 === a.wordSeparator && (f = " "), e.trim([n, c, o].join(f))
                },
                parse: function(t) {
                    var i = e.trim(t);
                    return i = i.replace(/\.\d+/, ""), i = i.replace(/-/, "/").replace(/-/, "/"), i = i.replace(/T/, " ").replace(/Z/, " UTC"), i = i.replace(/([\+\-]\d\d)\:?(\d\d)/, " $1$2"), new Date(i)
                },
                datetime: function(t) {
                    var i = o.isTime(t) ? e(t).attr("datetime") : e(t).attr("title");
                    return o.parse(i)
                },
                isTime: function(t) {
                    return "time" === e(t).get(0).tagName.toLowerCase()
                }
            });
            var r = {
                init: function() {
                    var i = e.proxy(t, this);
                    i();
                    var a = o.settings;
                    a.refreshMillis > 0 && setInterval(i, a.refreshMillis)
                },
                update: function(i) {
                    e(this).data("timeago", {
                        datetime: o.parse(i)
                    }), t.apply(this)
                }
            };
            e.fn.timeago = function(e, t) {
                var i = e ? r[e] : r.init;
                if (!i) throw new Error("Unknown function name '" + e + "' for timeago");
                return this.each(function() {
                    i.call(this, t)
                }), this
            }, document.createElement("abbr"), document.createElement("time")
        }), function() {
            function e(e, t, i, a) {
                var n = e % 10;
                return 1 == n && (1 == e || e > 20) ? t : n > 1 && 5 > n && (e > 20 || 10 > e) ? i : a
            }
            jQuery.timeago.settings.strings = {
                prefixAgo: null,
                prefixFromNow: "через",
                suffixAgo: "тому",
                suffixFromNow: null,
                seconds: "менше хвилини",
                minute: "хвилину",
                minutes: function(t) {
                    return e(t, "%d хвилина", "%d хвилини", "%d хвилин")
                },
                hour: "година",
                hours: function(t) {
                    return e(t, "%d година", "%d години", "%d годин")
                },
                day: "день",
                days: function(t) {
                    return e(t, "%d день", "%d дні", "%d днів")
                },
                month: "місяц",
                months: function(t) {
                    return e(t, "%d місяць", "%d місяця", "%d місяців")
                },
                year: "рік",
                years: function(t) {
                    return e(t, "%d рік", "%d роки", "%d років")
                }
            }
        }(), $("time.ago").timeago(), $(window).width() > 950) {
        var e = $("#movie-left").attr("data-bg");
        e && ($("#all-wrap").addClass("have-bg").prepend('<div id="bg-wallpaper"></div>'), $("#bg-wallpaper").css({
            background: "url(" + e + ") center top fixed no-repeat",
            "background-size": "cover"
        }), $(".have-bg #full-wr").css({
            "padding-top": "300px"
        }), $(window).load(function() {
            $("#bg-wallpaper").animate({
                opacity: 1
            }, 2e3)
        }))
    }
});

$(document).ready(function() {
    $("body").on("click", "#nav-load a", function() {
        var o = $(this).attr("href"),
            t = $(this).offset().top - 200;
        return void 0 !== o && $.ajax({
            url: o,
            beforeSend: function() {
                ShowLoading("")
            },
            success: function(n) {
                $("#bottom-nav").remove(),
                $("#dle-content").append(n),
                //$("#dle-content").append($("#dle-content", n).html()),


                window.history.pushState("", "", o),

                $("html, body").animate({
                    scrollTop: t
                }, 800), HideLoading("")
            },
            error: function() {
                HideLoading(""), alert("щось пішло не так")
            }
        }), !1
    })
});

$(document).ready(function() {
    var o = $(window),
        i = $(".side-tabs"),
        d = $(".side-content-adblock");
    if (i.length) {
        o.scroll(function(t) {
            var n = o.scrollTop();
            i.offset().top + i.height() - 95 <= n ? d.addClass("side-fixed") : d.removeClass("side-fixed")
        })
    }
});

function epscapeShowHide() {
    return $(".epscape_tr").css("display", ""), $("#epscape_showmore").html(""), !1
}

function ShowOrHideEp(e, t) {
    var a = $("#" + e);
    e = document.getElementById("image-" + e) ? document.getElementById("image-" + e) : null;
    var i = a.height() / 200 * 1e3;
    3e3 < i && (i = 3e3), 250 > i && (i = 250), "none" == a.css("display") ? $("#showhide_" + t).html("закрити") : $("#showhide_" + t).html("відкрити"), "none" == a.css("display") ? (a.show("blind", {}, i), e && (e.src = dle_root + "templates/" + dle_skin + "/dleimages/spoiler-minus.gif")) : (2e3 < i && (i = 2e3), a.hide("blind", {}, i), e && (e.src = dle_root + "templates/" + dle_skin + "/dleimages/spoiler-plus.gif"))
}

function createFavList() {
    $.magnificPopup.open({
        items: {
            src: "<div id='fav-create-list'><input type='text' name='fav-list-name' class='fav-list-name'/><label><input type='checkbox' name='fav-list-private' class='fav-list-private'/> Приватний список</label><div class=\"foot\"><center><button onclick=\"saveFavList('create')\">Створити</button></center></div></div>"
        },
        type: "inline",
        mainClass: "mfp-fade",
        removalDelay: 170,
        modal: !1,
        showCloseBtn: !0,
        callbacks: {
            open: function() {},
            afterClose: function() {},
            beforeClose: function() {}
        }
    })
}

function deleteFavList(e) {
    DLEconfirm("Ви дійсно хочете видалити список?", dle_confirm, function() {
        ShowLoading(""), $.post(dle_root + "engine/ajax/controller.php?mod=favorites_list", {
            act: "delete",
            id: e,
            user_hash: dle_login_hash
        }, function(t) {
            HideLoading(""), "del ok" == t ? ($('[data-status="' + e + '"]').remove(), DLEalert("Вдало", "Видалення списку")) : DLEalert("Помилка", "Видалення списку")
        })
    })
}

function saveFavList(e, t) {
    let a = $(".fav-list-name").val(),
        i = $(".fav-list-private:checked").length ? 1 : 0;
    "" != a ? (ShowLoading(""), $.get(dle_root + "engine/ajax/controller.php?mod=favorites_list", {
        act: e,
        name: a,
        st_private: i,
        id: t,
        user_hash: dle_login_hash
    }, function(t) {
        HideLoading(""), "ok" == t.success ? ($.magnificPopup.close(), "create" == e ? $(".user-lists").append('<label class="option add-trigger u-option" data-status="' + t.type + '"><input type="checkbox" onchange="updUFavList(this, ' + t.type + ');" value="1"/><div class="text"><span class="status-name" data-text="' + t.name + '">' + t.name + '</span></div><span class="removelist" onclick="deleteFavList(' + t.type + ')">×</span></label>') : DLEalert("Вдало", "Збереження списку")) : "create" == e ? DLEalert("Помилка", "Створення списку") : DLEalert("Помилка", "Збереження списку")
    }, "json")) : DLEalert("Помилка", "Поле не може бути пустим")
}

function editFavList(e) {
    (e = e || 0) && (ShowLoading(""), $.get(dle_root + "engine/ajax/controller.php?mod=favorites_list", {
        act: "edit",
        id: e,
        user_hash: dle_login_hash
    }, function(e) {
        if (HideLoading(""), !e.error) {
            let t = e.name,
                a = 1 == e.st_private ? " checked" : "",
                i = e.id ? e.id : 0;
            $.magnificPopup.open({
                items: {
                    src: "<div id='fav-create-list'><input type='text' name='fav-list-name' class='fav-list-name' value='" + t + "'/><label><input type='checkbox' name='fav-list-private' class='fav-list-private'" + a + '/> Приватний список</label><div class="foot"><center><button onclick="saveFavList(\'save\', ' + i + ')">Зберегти</button></center></div></div>'
                },
                type: "inline",
                mainClass: "mfp-fade",
                removalDelay: 170,
                modal: !1,
                showCloseBtn: !0,
                callbacks: {
                    open: function() {},
                    afterClose: function() {},
                    beforeClose: function() {}
                }
            })
        }
    }, "json"))
}

function updUFavList(e, t, a) {
    ShowLoading("");
    a = a || "main";
    let i = $(e).prop("checked") ? "plus" : "minus",
        n = $(e).parents(".addlist"),
        o = $(n).data("news");
    $.post(dle_root + "engine/ajax/controller.php?mod=favorites", {
        fav_id: o,
        action: i,
        type: t,
        skin: dle_skin,
        alert: 1,
        zone: a,
        user_hash: dle_login_hash
    }, function(e) {
        HideLoading("");
        let t = $(n).children()[0],
            a = !!$(t).data("active") && $(t).data("active");
        "not-favorites-list" != e ? (a && ($(t).removeAttr("data-active").removeClass(a), $(n).find(".favorites-addnote, .remove-trigger").hide()), "minus" == i ? $(n).find(".edit-trigger .status-name").text("Добавить в список").attr("onclick", "updFavList(this, 'plus', 5);") : $(n).find(".edit-trigger .status-name").text("Добавлено").removeAttr("onclick")) : DLEalert("Списка не існує", "Збереження списку")
    })
}

function updFavList(e, t, a, i) {
    ShowLoading("");
    i = i || "main";
    let n = $(e).parents(".addlist").data("news");
    $.post(dle_root + "engine/ajax/controller.php?mod=favorites", {
        fav_id: n,
        action: t,
        type: a,
        skin: dle_skin,
        alert: 1,
        zone: i,
        user_hash: dle_login_hash
    }, function(e) {
        HideLoading(""), "not-favorites-list" != e ? $(".fav-id-" + n).html(e) : DLEalert("Списка не існує", "Видалення списку")
    })
}

function updFavNoteSave(e) {
    ShowLoading(""), $.get(dle_root + "engine/ajax/controller.php?mod=favorites", {
        fav_id: e,
        note: $('textarea[name="fav-note-text"]').val() ? $('textarea[name="fav-note-text"]').val() : "",
        action: "updNote",
        user_hash: dle_login_hash
    }, function(e) {
        HideLoading("")
    })
}

function updFavNoteEdit(e) {
    $.magnificPopup.open({
        items: {
            src: "<div id='fav-note'><textarea name='fav-note-text' class='fav-note-text'>" + ($(".fav-id-" + e + " .note").text() ? $(".fav-id-" + e + " .note").text() : "") + '</textarea><div class="note-foot"><center><button onclick="updFavNoteSave(' + e + ')">Зберегти</button></center></div></div>'
        },
        type: "inline",
        mainClass: "mfp-fade",
        removalDelay: 170,
        modal: !1,
        showCloseBtn: !0,
        callbacks: {
            open: function() {},
            afterClose: function() {},
            beforeClose: function() {}
        }
    })
}

function showAlert(text){
	var err = $('<div>'+text+'</div>');
	err.prependTo(".show-alerts");
	err.slideDown(300);
	setTimeout(function(){err.fadeOut(500,function(){$(this).remove()})},3000);
}
function showLoad(show){
	$(".showLoad").remove();
	if(show) $(".show-alerts").append('<div class="showLoad"></div>');
}
$(function(){
	$('body').append('<div class="show-alerts"></div>');
})

var od_delay=null;
$(document)
.on('click','.orderdesc-add',function(e){
	var $this = $(this);
	if($this.hasClass('current')){
		$this.removeClass('current');
		$(".orderdesc-add-area").slideUp(300);
		return false;
	}
	showLoad(1);
	$.post(dle_root+"engine/mods/orderdesc/ajax.php",{action:'form'},function(d){
		showLoad(0);
		var cd = d.split("::");
		if(cd[0]=='.') showAlert(cd[1]);
		else{
			$(".orderdesc-add-area").html(d).slideDown(300);
			$this.addClass('current');
		}
	})
	e.preventDefault();
})
.on('click','.orderdesc-cancel',function(e){
	$(".orderdesc-add-area").slideUp(300,function(){$(this).html('')});
	$(".orderdesc-add").removeClass('current');
	e.preventDefault();
})
.on('keyup','#orderdescTitle',function(e){
	var title = $("#orderdescTitle").val();
	clearTimeout(od_delay);
	if(title.length>2){
		od_delay = setTimeout(function(){
			showLoad(1);
			$.post(dle_root+"engine/mods/orderdesc/ajax.php",{title:title,action:'related'},function(d){
				showLoad(0);
				var cd = d.split("::");
				if(cd[0]=='.') showAlert(cd[1]);
				else $(".orderdesc-related").html(d).slideDown(300);
			})
		},400)
	}else $(".orderdesc-related").slideUp(300,function(){$(this).html('')});
})
.on('click','.orderdesc-doadd',function(e){
	var title = $("#orderdescTitle").val();
	var imdb = $("#orderdescIMDB").val();
	imdb = imdb.replace(/[^0-9]/g, '');
	var source = $("#orderdescSource").val();
	
	if(title.length<3){
		showAlert('Заголовок закороткий');
		return false;
	}
	if(imdb.length<5){
		showAlert('IMDB id вказано неправильно');
		return false;
	}
	if(source.length<5){
		showAlert('Без джерела ми не зможемо додати ваше замовлення :(');
		return false;
	}
	showLoad(1);
	var subscribe = $("#orderdescV1").prop('checked')?1:0;
	var anonim = $("#orderdescV2").prop('checked')?1:0;
	$.post(dle_root+"engine/mods/orderdesc/ajax.php",{title:title,imdb:imdb,action:'add',subscribe:subscribe,anonim:anonim,hash:$(".orderdesc-hash").val(),source:source},function(d){
		showLoad(0);
		var cd = d.split("::");
		if(cd[0]=='.') showAlert(cd[1]);
		else{
			showAlert('Ваш заказ успешно добавлен');
			$(".orderdesc-add").removeClass('current');
			$(".orderdesc-table").prepend(d);
			$(".orderdesc-add-area").slideUp(300,function(){$(this).html('')});
		}
	})
	e.preventDefault();
})
.on('click','.orderdesc-mass-sel',function(){
	if($(this).prop('checked')) $(".orderdesc-mass").prop('checked',true);
	else $(".orderdesc-mass").prop('checked',false);
})
.on('click','.orderdesc-rating',function(){
	var $this = $(this);
	showLoad(1);
	$.post(dle_root+"engine/mods/orderdesc/ajax.php",{action:'rating',id:$this.data('id')},function(d){
		showLoad(0);
		var cd = d.split("::");
		if(cd[0]=='.') showAlert(cd[1]);
		else{
			$this.html(parseInt($this.text())+1);
			$this.addClass('orderdesc-rating-green');
		}
	})
})
.on('click','.orderdesc-edit',function(e){
	var id = $(this).data('id');
	$("#orderdesc-edit").remove();
	$("body").append('<div id="orderdesc-edit" title="Редактирование заказа #'+id+'"/>')
	showLoad(1);
	$.post(dle_root+"engine/mods/orderdesc/ajax.php",{action:'edit',id:id},function(d){
		showLoad(0);
		var cd = d.split("::");
		if(cd[0]=='.') showAlert(cd[1]);
		else{
			$("#orderdesc-edit").html(d).dialog({
				width: '720px',
				buttons: {
					"Видалити?":function(e){
						var b = $(e.currentTarget);
						if(!b.hasClass('confirm')){
							b.addClass('confirm').find('span').html('Точно видалити');
							return false;
						}
						showLoad(1);
						$.post(dle_root+"engine/mods/orderdesc/ajax.php",{action:'delete',id:id},function(d){
							showLoad(0);
							var cd = d.split("::");
							if(cd[0]=='.') showAlert(cd[1]);
							else{
								$("#odrow-"+id).remove();
								$("#orderdesc-edit").dialog('close');
							}
						})
					},"Скасувати":function(){
						$(this).dialog('close');
					},"Зберегти":function(){
						var subscribe = $("#od-V1").prop('checked')?1:0;
						var anonim = $("#od-V2").prop('checked')?1:0;
						var sendemail = $("#od-V3").prop('checked')?1:0;
						var status = $(".od-status:checked").val();
						var link = $("#od-link").val();
						var reason = $("#od-reason").val();
						if(status==1 && link.length<1){
							showAlert('Не добавлена ссылка на комикс');
							return false;
						}
						if(status==2 && reason.length<1){
							showAlert('Не вказана причина відмови');
							return false;
						}
						showLoad(1);
						$.post(dle_root+"engine/mods/orderdesc/ajax.php",{action:'doedit',id:id,title:$("#od-title").val(),imdb:$("#od-imdb").val(),source:$("#od-source").val(),date:$("#od-date").val(),author:$("#od-author").val(),status:status,link:link,reason:reason,subscribe:subscribe,anonim:anonim,sendemail:sendemail},function(d){
							showLoad(0);
							var cd = d.split("::");
							if(cd[0]=='.') showAlert(cd[1]);
							else{
								showAlert("Зміни збережено");
								$("#odrow-"+id).html($(d,"tr").html());
								$("#orderdesc-edit").dialog('close');
							}
						})
					}
					
				}
			})
		}
	})
	e.preventDefault();
})


function topage() {
    var c = {};
    c[dle_act_lang[3]] = function() {
        $(this).dialog("close");
    };
    c[dle_p_send] = function() {
        if ($.isNumeric($("#dle-promt-text").val()) === false) {
            $("#dle-promt-text").addClass("ui-state-error");
            return false;
        } else {
            var c = $("#dle-promt-text").val();
            document.location = window.location.pathname.replace(/page\/[0-9]+\//, '') + "page\/" + c + "\/";
        }
    };
    $("#dlepopup").remove();
    $("body").append("<div id='dlepopup' title='Перехід на іншу сторінку' style='display:none'><p>Введіть номер сторінки для переходу:</p><input type='text' name='dle-promt-text' id='dle-promt-text' class='ui-widget-content ui-corner-all' style='width:97%; padding: .4em;' value=''/></div>");
    $("#dlepopup").dialog({
        autoOpen: true,
        resizable: false,
        show: 'fade',
        hide: 'fade',
        width: 470,
        open: function() {
            $(this).keypress(function(e) {
                if (e.keyCode == $.ui.keyCode.ENTER) {
                    $(this).parent().find('.ui-dialog-buttonpane button:last').trigger("click");
                }
            });
        },
        buttons: c
    })

    return false;
};

function getCommLink(a) {
	const url = new URL(location.href) //(location.href);
	url.hash= "";

	if ($(a).attr('href').indexOf("/comment/") == -1) url.hash = $(a).attr('href');
	else url.pathname = $(a).attr('href');
	
    DLEalert('Посилання на коментар:<br /><input type=\'text\' name=\'dle-comm-link\' id=\'dle-comm-link\' class=\'ui-widget-content ui-corner-all\' style=\'width:97%;\' value=\'' + url + '\'/>', 'Посилання');
    $("#dle-comm-link").select().focus();
    return false;
};

function doAuthMe() {
	var rndval = new Date().getTime(); 	

	ShowLoading('');

	$.post(dle_root + "engine/ajax/controller.php?mod=qr_code&rnd="+rndval, { qr_action: "create", skin: dle_skin, user_hash: dle_login_hash }, function(data){

		HideLoading('');
		
		if (data.success) {
			  let current = 0;
			  let timerId = setInterval(function() {

					$.post(dle_root + "engine/ajax/controller.php?mod=qr_code&rnd="+rndval, { qr_action: "check", skin: dle_skin, user_hash: dle_login_hash }, function(data){
					
						if (data.success) {					
							//window.location.reload();
							window.location.href = dle_root;
						}
					
					}, "json");
					
					
					if (current == 60) {
					  clearInterval(timerId);
					}
					current++;

			  }, 5000);	
		  		
			DLEalert(data.content, dle_info); 
			
		
	
		} else {
				
			DLEalert ( data.content, dle_info );
			
		}
		
	}, "json");

	return false;


};

function showans(el) {

	$(el).parent('ol').hide();	
	$(el).parent().siblings(".clvl-1").show();
		
	return false;
};