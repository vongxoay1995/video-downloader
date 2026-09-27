const {C,r,t,line,circ,i,iconButton,chip,button,photo,badge,bar,nav,header,screen,caption,board,writeBoard}=require('./ui-kit.cjs');

// Independent market-led Android UI. Screen coordinates are local, 400 × 860.
let downloads=header('Downloads','Your next watch is on its way.','plus');
downloads+=chip(24,137,108,'Active  3',true)+chip(140,137,128,'Completed');
downloads+=t(24,203,'DOWNLOADING NOW',11,700,C.sub)+t(376,203,'Pause all',13,700,C.primary,'end');
downloads+=r(24,218,352,279,C.white,24)+photo('coast',36,230,328,117,17)+badge(300,311,'08:42');
downloads+=t(40,374,'Coastal escape',16,700)+t(40,397,'MP4 · 1080p · 82 MB',12,400,C.sub);
downloads+=iconButton('pause',312,357,C.soft,C.primary);
downloads+=t(40,431,'51 MB / 82 MB',13,600)+t(360,431,'62%',14,700,C.primary,'end')+bar(40,444,320,.62);
downloads+=i('bolt',40,466,15,C.primary)+t(62,478,'2.6 MB/s',12,700,C.primary)+t(360,478,'About 12 sec left',12,400,C.sub,'end');
downloads+=r(24,514,352,98,C.white,22)+photo('city',36,530,64,64,14)+t(116,548,'City after dark',15,700)+t(116,570,'Paused · 14 / 44 MB',12,400,C.sub)+bar(116,584,174,14/44,C.sub,C.line,4)+iconButton('play',316,536,C.bg);
downloads+=r(24,628,352,98,C.white,22)+photo('coffee',36,644,64,64,14)+t(116,661,'Slow Sunday playlist',15,700)+t(116,684,'Audio · 36 MB',12,400,C.sub)+t(116,704,'Queued · starts next',11,600,C.primary)+i('more',328,664,24,C.sub);
downloads+=i('info',60,742,15,C.sub)+t(82,754,'Keeps downloading in the background',11,400,C.sub)+nav('Downloads');

let library=header('Library','All your favorites. No connection needed.','search');
library+=r(24,136,352,43,C.white,14)+i('folder',38,148,18,C.primary)+t(65,163,'1.2 GB saved',13,600)+t(362,163,'Manage',12,700,C.primary,'end');
library+=chip(24,195,69,'All',true)+chip(101,195,106,'Videos  12')+chip(215,195,99,'Audio  2');
library+=t(24,273,'Continue watching',18,700)+t(376,273,'1 video',12,400,C.sub,'end');
library+=photo('coast',24,290,352,174,22)+`<rect x="24" y="363" width="352" height="101" rx="22" fill="${C.dark}" opacity="0.72"/>`;
library+=circ(342,325,20,'#FFFFFF')+i('play',331,314,22,C.ink)+t(40,406,'Coastal escape',16,700,C.white)+t(40,428,'02:16 / 08:42 · 1080p',12,400,'#DEE3ED')+bar(40,446,320,.26,'#C9C2FF','#67717E',4);
library+=t(24,511,'Recently saved',18,700)+i('sort',349,493,23,C.sub);
library+=photo('mountain',24,530,168,109,18)+badge(132,603,'02:14')+t(24,663,'Somewhere quieter',14,700)+t(24,686,'1080p · 124 MB',11,400,C.sub)+i('more',171,648,20,C.sub);
library+=photo('city',208,530,168,109,18)+badge(316,603,'03:16')+t(208,663,'City after dark',14,700)+t(208,686,'720p · 44 MB',11,400,C.sub)+i('more',355,648,20,C.sub);
library+=r(24,712,352,42,C.white,14)+i('headphones',38,724,18,C.primary)+t(65,738,'Your audio collection',13,600)+t(335,738,'2',12,700,C.sub)+i('chevron',350,725,16,C.sub)+nav('Library');

let search=iconButton('back',12,50,C.bg)+r(68,50,308,48,C.white,16)+i('search',82,65,19,C.sub)+t(111,80,'coast',16,600)+i('close',342,66,18,C.sub);
search+=t(24,134,'Search your offline collection',13,400,C.sub)+chip(24,156,77,'All',true)+chip(109,156,85,'Videos')+chip(202,156,83,'Audio');
search+=t(24,237,'1 result for “coast”',18,700)+t(24,262,'Matched by title',12,400,C.sub);
search+=photo('coast',24,285,352,206,22)+badge(308,451,'08:42')+circ(200,382,29,C.white)+i('play',186,368,29,C.ink);
search+=t(24,526,'Coastal escape',18,700)+i('more',351,506,23,C.sub)+t(24,550,'MP4 · 1080p · 82 MB',13,400,C.sub);
search+=r(24,574,352,54,C.mint,16)+i('check',40,591,19,C.green)+t(70,597,'Ready to watch offline',13,600,C.green);
search+=t(200,711,'Find it fast, even without Wi-Fi.',13,400,C.sub,'middle')+nav('Library');

let empty=header('Library','A little space for your favorite videos.','search');
empty+=chip(24,137,69,'All',true)+chip(101,137,85,'Videos')+chip(194,137,81,'Audio');
// Purposeful empty-state illustration: a saved video in a personal collection.
empty+=circ(200,354,105,C.soft)+r(109,294,159,135,'#DDD8FF',24)+r(128,275,160,135,C.white,23)+r(144,290,128,78,'#D4CDFB',15);
empty+=`<path d="M144 346 L181 316 L207 338 L233 306 L272 345 V353 Q272 368 257 368 H159 Q144 368 144 353Z" fill="#9F94EB"/>`+circ(250,311,9,'#F7E3AA');
empty+=circ(209,329,19,C.white)+i('play',198,318,22,C.primary)+r(146,385,71,5,C.line,2)+r(146,397,109,4,C.line,2);
empty+=circ(280,404,25,C.primary)+i('download',268,392,24,C.white);
empty+=t(200,505,'Your offline era starts here',22,700,C.ink,'middle')+t(200,538,'Save a video you love, then find it here.',13,400,C.sub,'middle')+t(200,560,'Ready whenever you are.',13,400,C.sub,'middle');
empty+=button(48,596,304,'Save your first video','plus')+t(200,680,'Have a link? Paste it on Home.',12,400,C.sub,'middle')+nav('Library');

let completed=header('Downloads','Saved and ready for wherever you go.','plus');
completed+=chip(24,137,108,'Active  3')+chip(140,137,128,'Completed',true)+t(24,203,'TODAY',11,700,C.sub)+t(376,203,'Select',13,700,C.primary,'end');
completed+=r(24,218,352,301,C.white,24)+photo('coast',36,230,328,136,17)+badge(300,330,'08:42')+t(40,396,'Coastal escape',18,700)+i('check',40,413,17,C.green)+t(65,429,'Downloaded · 1080p · 82 MB',12,400,C.sub)+button(36,452,328,'Play now','play');
completed+=r(24,534,352,94,C.white,22)+photo('mountain',36,548,64,64,14)+t(116,560,'Somewhere quieter',15,700)+t(116,582,'1080p · 124 MB',12,400,C.sub)+t(116,603,'02:14 · Saved today',11,400,C.sub)+iconButton('play',316,557,C.bg);
completed+=r(24,642,352,94,C.white,22)+photo('city',36,656,64,64,14)+t(116,668,'City after dark',15,700)+t(116,690,'720p · 44 MB',12,400,C.sub)+t(116,711,'03:16 · Saved today',11,400,C.sub)+iconButton('play',316,665,C.bg);
completed+=nav('Downloads');

let noResults=iconButton('back',12,50,C.bg)+r(68,50,308,48,C.white,16)+i('search',82,65,19,C.sub)+t(111,80,'coastl',16,600)+i('close',342,66,18,C.sub);
noResults+=t(24,134,'Search your offline collection',13,400,C.sub)+chip(24,156,77,'All',true)+chip(109,156,85,'Videos')+chip(202,156,83,'Audio');
noResults+=circ(200,352,85,C.soft)+i('search',162,310,80,C.primary)+circ(137,292,9,'#DAD4FF')+circ(274,401,6,'#DAD4FF');
noResults+=t(200,493,'No matches for “coastl”',22,700,C.ink,'middle')+t(200,525,'Try a shorter title or another keyword.',13,400,C.sub,'middle')+button(48,560,304,'Clear search','close')+t(200,650,'Your saved videos are still in Library.',12,400,C.sub,'middle')+nav('Library');

let body=caption(48,215,'05','Downloads','Live progress, pause, resume & a clear queue')+caption(496,215,'06','Library','Resume playback & browse downloaded media')+caption(944,215,'07','Offline search','A fast route back to anything you saved')+caption(1392,215,'08','First-use empty state','One confident next step, no dead ends');
body+=screen('05-Downloads',48,250,downloads)+screen('06-Library',496,250,library)+screen('07-Offline-search',944,250,search)+screen('08-Empty-library',1392,250,empty);
body+=caption(48,1175,'09','Completed downloads','One-tap playback with a useful saved history')+caption(496,1175,'10','No search results','A helpful recovery, without losing the collection');
body+=screen('09-Completed-downloads',48,1210,completed)+screen('10-No-search-results',496,1210,noResults);

body+=t(944,1194,'A considered visual system',25,700)+t(944,1224,'Expressive where it helps. Quiet where content matters.',15,400,C.sub);
body+=r(944,1250,880,289,C.white,24)+t(972,1285,'COLOR & TYPE',11,700,C.sub);
const swatches=[[C.primary,'Primary','#6153E8'],[C.ink,'Ink','#192136'],[C.bg,'Canvas','#F8F9FC'],[C.soft,'Soft violet','#EEEBFF'],[C.green,'Success','#167D66']];
swatches.forEach((a,k)=>{let x=972+k*167;body+=r(x,1304,141,53,a[0],12,a[1]==='Canvas'?C.line:'none')+t(x,1379,a[1],13,700)+t(x,1399,a[2],11,400,C.sub);});
body+=line(972,1424,1796,1424)+t(972,1461,'Aa',30,700)+t(1039,1449,'Clear, familiar typography',16,700)+t(1039,1473,'29 / page title     18 / section     15 / action     13 / support',13,400,C.sub)+t(972,1512,'24 px screen margins · 48 px icon touch areas · 54 px primary actions',13,400,C.sub);

body+=t(944,1594,'Helpful at every handoff',25,700)+t(944,1624,'Short, specific feedback—always with the next action.',15,400,C.sub);
body+=r(944,1653,424,273,C.white,24)+r(968,1677,48,48,C.soft,16)+i('refresh',980,1689,24,C.primary)+t(1032,1708,'Checking your link…',19,700)+t(968,1760,'Finding available videos and quality options.',13,400,C.sub)+bar(968,1784,376,.43)+t(968,1824,'This usually takes a few seconds.',13,400,C.sub)+button(968,1850,376,'Cancel',null,'secondary');
body+=r(1400,1653,424,273,C.white,24)+r(1424,1677,48,48,C.warm,16)+i('alert',1436,1689,24,C.amber)+t(1488,1708,'This link isn’t supported',18,700)+t(1424,1756,'We couldn’t find a downloadable video.',13,400,C.sub)+t(1424,1780,'Try another link or open it in the browser.',13,400,C.sub)+button(1424,1810,376,'Try another link','link')+t(1612,1901,'Open in browser',13,700,C.primary,'middle');
body+=r(944,1954,880,116,C.soft,22)+i('info',968,1980,23,C.primary)+t(1008,1995,'Keep useful context, even when a task fails.',17,700)+t(1008,2026,'Preserve the link, title and current progress. Let people retry without starting over.',13,400,C.sub);

body+=r(48,2130,1776,100,C.white,22);
body+=i('check',71,2162,22,C.green)+t(104,2173,'Finished',14,700)+t(104,2195,'Moves to Library · “Play now” action',12,400,C.sub);
body+=i('refresh',502,2162,22,C.primary)+t(536,2173,'Interrupted',14,700)+t(536,2195,'Keep progress · offer Resume or Retry',12,400,C.sub);
body+=i('alert',948,2162,22,C.amber)+t(982,2173,'Link expired',14,700)+t(982,2195,'Keep the item · ask for a fresh link',12,400,C.sub);
body+=i('folder',1395,2162,22,C.primary)+t(1429,2173,'Storage full',14,700)+t(1429,2195,'Pause safely · show Manage storage',12,400,C.sub);
writeBoard('v3-02-library',board('Your collection, always within reach.','Clear download states. A visual offline library. Less managing, more watching.',body,1872,2280));
