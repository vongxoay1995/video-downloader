const {C,r,t,line,circ,i,iconButton,button,photo,bar,nav,screen,caption,board,writeBoard}=require('./ui-kit.cjs');
const darkCard='#1E2736', darkLine='#303A4D';
const darkBtn=(name,x,y)=>iconButton(name,x,y,darkCard,C.white);
const darkTop=(label)=>darkBtn('back',24,48)+t(200,77,label,11,700,C.muted,'middle')+darkBtn('more',328,48);
const pill=(x,y,w,label,fill=C.mint,ink=C.green)=>r(x,y,w,29,fill,14)+t(x+w/2,y+19,label,11,700,ink,'middle');
const toggle=(x,y,on)=>r(x,y,46,28,on?C.primary:'#C7CDDB',14)+circ(x+(on?32:14),y+14,10,C.white);
const settingRow=(y,icon,title,subtitle,value=null,on=null)=>i(icon,38,y+19,22,C.primary)+t(78,y+24,title,15,700)+t(78,y+47,subtitle,12,400,C.sub)+(value?t(339,y+24,value,12,600,C.sub,'end')+i('chevron',346,y+14,19,C.sub):'')+(on!==null?toggle(306,y+15,on):'');

const player=darkTop('NOW PLAYING')
  +photo('coast',24,120,352,298,22)
  +pill(38,135,72,'OFFLINE','#FFFFFF',C.ink)
  +t(24,465,'Coastal escape',27,700,C.white)
  +t(24,493,'Travel journal · 1080p',14,400,C.muted)
  +bar(24,530,352,0.26,'#B3A9FF',darkLine,5)+circ(115.5,532.5,6,C.white)
  +t(24,558,'02:16',12,600,C.white)+t(376,558,'08:42',12,400,C.muted,'end')
  +r(69,590,52,52,darkCard,26)+t(95,622,'−10s',13,600,C.white,'middle')
  +circ(200,616,37,C.primary)+i('pause',184,600,32,C.white)
  +r(279,590,52,52,darkCard,26)+t(305,622,'+10s',13,600,C.white,'middle')
  +r(24,682,352,76,darkCard,20)
  +t(70,711,'1.0×',17,700,C.white,'middle')+t(70,738,'Speed',11,400,C.muted,'middle')
  +i('captions',145,691,24,C.white)+t(157,738,'Subtitles',11,400,C.muted,'middle')
  +i('lock',233,691,24,C.white)+t(245,738,'Lock',11,400,C.muted,'middle')
  +i('expand',321,691,24,C.white)+t(333,738,'Full screen',11,400,C.muted,'middle')
  +t(200,806,'Saved on this device',12,400,C.muted,'middle')+r(146,847,108,4,C.white,2);

const settings=iconButton('back',24,48,C.white)+t(24,135,'Settings',30,700)
  +t(24,163,'Make every download feel effortless.',14,400,C.sub)
  +t(24,211,'DOWNLOADS',11,700,C.sub)
  +r(24,227,352,270,C.white,22)
  +settingRow(235,'wifi','Wi-Fi only','Pause when using mobile data',null,true)
  +line(78,303,352,303)
  +settingRow(315,'download','Default quality','Ask each time','Ask')
  +line(78,381,352,381)
  +settingRow(393,'folder','Save location','Device / Downloads','')
  +i('chevron',346,413,19,C.sub)
  +t(78,477,'18.4 GB available',12,600,C.green)
  +t(24,536,'PLAYBACK',11,700,C.sub)
  +r(24,552,352,156,C.white,22)
  +settingRow(560,'play','Resume playback','Continue where you left off',null,true)
  +line(78,630,352,630)
  +settingRow(638,'moon','Appearance','Match your device','System')
  +r(24,734,352,56,C.soft,18)+i('shield',40,750,22,C.primary)
  +t(76,757,'Your media stays on your device.',13,600,C.ink)
  +t(76,776,'No account needed.',11,400,C.sub)
  +t(200,825,'BrightFetch · Version 1.0',11,400,C.sub,'middle')+r(146,847,108,4,C.ink,2);

const recovery=iconButton('back',24,48,C.white)+t(87,78,'Download details',20,700)+iconButton('more',328,48,C.white)
  +r(24,119,352,119,C.white,22)+photo('coast',36,131,96,95,14)
  +t(148,153,'Coastal escape',16,700)+t(148,179,'MP4 · 1080p · 82 MB',11,400,C.sub)
  +pill(148,194,119,'LINK EXPIRED',C.warm,C.amber)
  +r(24,260,352,239,C.white,22)
  +circ(59,300,19,C.warm)+i('link',48,289,22,C.amber)
  +t(24+24,350,'This link has expired',23,700)
  +t(48,381,'Open the original page to get a fresh link.',13,400,C.sub)
  +bar(48,411,304,0.62,'#B1A8F3',C.soft,6)
  +t(48,441,'51 MB kept',13,600)+t(352,441,'62%',13,700,C.primary,'end')
  +t(48,470,'Your download is paused, not lost.',12,400,C.sub)
  +t(24,541,'Pick up where you left off',19,700)
  +t(24,568,'We’ll try to resume after the link refreshes.',13,400,C.sub)
  +t(24,590,'If the file has changed, a new download starts.',13,400,C.sub)
  +button(24,618,352,'Open source page','globe')
  +button(24,684,352,'Replace with a new link','link','secondary')
  +nav('Downloads');

const playerError=darkTop('PLAYER')
  +`<g opacity="0.3">${photo('coast',24,120,352,249,22)}</g>`
  +circ(200,244,32,'#293246')+i('alert',186,230,28,'#E3DFFF')
  +t(200,418,'Can’t play this file',25,700,C.white,'middle')
  +t(200,451,'The file may be incomplete or use',14,400,C.muted,'middle')
  +t(200,475,'a format this player doesn’t support.',14,400,C.muted,'middle')
  +r(24,513,352,72,darkCard,19)+i('info',41,537,23,C.muted)
  +t(81,541,'Coastal escape.mp4',14,600,C.white)
  +t(81,564,'82 MB · Saved on this device',12,400,C.muted)
  +button(24,615,352,'Try again','refresh')
  +r(24,682,352,54,darkCard,17)+i('folder',42,697,24,C.white)+t(208,715,'Open another file',15,700,C.white,'middle')
  +t(200,786,'Your saved file won’t be removed.',12,400,C.muted,'middle')+r(146,847,108,4,C.white,2);

let landscape=`<g id="landscape-player" transform="translate(48 1212)">`
  +photo('coast',0,0,720,360,24)+`<rect width="720" height="360" rx="24" fill="#111722" opacity=".25"/>`
  +`<defs><linearGradient id="landscapeShade" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0A111F" stop-opacity=".55"/><stop offset=".45" stop-color="#0A111F" stop-opacity="0"/><stop offset="1" stop-color="#0A111F" stop-opacity=".95"/></linearGradient></defs>`
  +r(0,0,720,360,'url(#landscapeShade)',24)+i('back',20,22,24,C.white)+t(65,42,'Coastal escape',17,700,C.white)+i('lock',650,22,23,C.white)
  +circ(360,165,32,'#6153E8')+i('pause',347,152,26,C.white)
  +t(24,279,'02:16',12,600,C.white)+t(696,279,'08:42',12,600,C.white,'end')
  +bar(24,293,672,0.26,'#B3A9FF','#FFFFFF44',4)+circ(198.7,295,5,C.white)
  +i('volume',24,316,22,C.white)+t(70,333,'1.0×',13,700,C.white)+i('captions',618,315,24,C.white)+i('expand',667,315,24,C.white)
  +'</g>';
const note=(y,n,title,body)=>circ(872,y-5,15,C.soft)+t(872,y,n,11,700,C.primary,'middle')+t(901,y-1,title,17,700)+t(901,y+26,body,14,400,C.sub);
let notes=t(848,1240,'Made for watching, not managing.',25,700)
  +note(1292,'01','One clear playback control','No competing play buttons. Chrome fades while watching.')
  +note(1370,'02','Keep the picture while buffering','Retain the last frame and playback position; show one spinner.')
  +note(1448,'03','Lock the screen, keep the moment','Hide touch controls. Press and hold the lock to bring them back.')
  +r(848,1512,928,60,C.white,18)+i('check',868,1530,22,C.green)
  +t(905,1538,'Recovery is part of the experience.',15,700)+t(905,1559,'Explain what happened, preserve context, and show the next useful action.',12,400,C.sub);
let body=caption(48,215,'11','Offline player','Photo-led playback with a single control hierarchy')
  +caption(496,215,'12','Settings','Useful defaults and local-first preferences')
  +caption(944,215,'13','Expired-link recovery','Keep context and offer a clear way back')
  +caption(1392,215,'14','Playback error','Helpful recovery, without dead controls')
  +screen('offline-player',48,250,player,true)+screen('settings',496,250,settings)
  +`<defs><clipPath id="recoveryScreenClip">${r(944,250,400,860,'#FFF',30)}</clipPath></defs><g clip-path="url(#recoveryScreenClip)">${screen('expired-link-recovery',944,250,recovery)}</g>`+screen('playback-error',1392,250,playerError,true)
  +caption(48,1160,'15','Landscape player','The content gets the space; controls get out of the way')
  +landscape+notes+t(48,1640,'03 / WATCH, PERSONALIZE & RECOVER',12,700,C.sub)+t(1792,1640,'400 × 860 Android screens · Editable vector layout',12,400,C.sub,'end');
writeBoard('v3-03-player',board('Watch it your way.','Quiet playback, thoughtful defaults, and recovery that keeps people moving.',body,1872,1690));
