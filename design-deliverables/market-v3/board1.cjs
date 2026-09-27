const {C,r,t,line,circ,i,iconButton,chip,button,photo,badge,bar,status,nav,header,screen,caption,board,writeBoard}=require('./ui-kit.cjs');
let home=r(24,53,36,36,C.primary,12)+i('download',32,61,20,C.white)+t(70,79,'BrightFetch',21,700)+iconButton('settings',330,47,C.white);
home+=t(24,136,'Save a video.',32,700)+t(24,165,'Paste a link. Keep it for later.',16,400,C.sub);
home+=r(24,190,352,56,C.white,17,C.line)+i('link',42,207,22,C.sub)+t(76,224,'Paste a video link',15,400,C.sub)+button(24,258,352,'Paste link','clipboard');
home+=r(24,324,352,50,C.soft,17)+i('globe',42,338,22,C.primary)+t(76,354,'Or explore in browser',15,600,C.primary)+i('arrow',340,338,22,C.primary);
home+=t(24,419,'Your shortcuts',18,700)+t(376,419,'Edit',13,600,C.primary,'end');
for(const [j,label,icon] of [[0,'Explore','globe'],[1,'Favorites','heart'],[2,'Recent','refresh'],[3,'Add site','plus']]){const x=24+j*92;home+=r(x,438,76,56,j===3?'#F0F1F6':C.white,17,j===3?C.line:'none')+i(icon,x+26,454,24,j===0?C.primary:C.ink)+t(x+38,516,label,12,500,C.sub,'middle');}
home+=t(24,562,'Recently saved',18,700)+t(376,562,'See all',13,600,C.primary,'end');
home+=photo('coast',24,581,170,113,16)+badge(136,660,'08:42')+t(24,720,'Coastal escape',15,700)+t(24,742,'1080p · 82 MB',12,400,C.sub);
home+=photo('city',206,581,170,113,16)+badge(318,660,'03:16')+t(206,720,'City after dark',15,700)+t(206,742,'720p · 44 MB',12,400,C.sub)+nav('Home');
let browser=iconButton('back',12,46,C.bg)+r(68,49,252,44,C.white,14,C.line)+i('lock',80,62,17,C.sub)+t(105,76,'fieldnotes.example',13,600)+r(334,55,28,30,'none',8,C.ink)+t(348,76,'2',13,700,C.ink,'middle');
browser+=r(0,108,400,555,C.white)+t(24,143,'FIELD NOTES',13,700)+t(376,143,'Journal  /  Films',11,500,C.sub,'end')+line(24,159,376,159);
browser+=t(24,196,'A little closer',31,700)+t(24,232,'to the ocean.',31,700)+photo('coast',24,254,352,246,20)+circ(200,377,30,'#FFFFFF')+i('play',189,365,24,C.ink)+badge(314,462,'08:42');
browser+=t(24,533,'Coastal escape',19,700)+t(24,558,'A short film by The Weekend Journal',13,400,C.sub)+r(24,583,352,69,C.bg,16)+photo('mountain',34,593,62,49,10)+t(110,611,'Up next',11,600,C.sub)+t(110,636,'Somewhere quieter',14,700)+i('chevron',340,607,22);
browser+=r(16,674,368,82,C.ink,24)+r(30,691,48,48,'#35314F',16)+i('download',42,703,24,'#D0C6FF')+t(90,704,'2 videos found',16,700,C.white)+t(90,728,'Ready to save from this page',11,400,'#BDC5D8')+r(301,694,68,43,C.primary,14)+t(335,721,'View',14,700,C.white,'middle');
browser+=r(0,776,400,84,C.white)+iconButton('back',19,786)+iconButton('chevron',99,786)+iconButton('home',176,786)+iconButton('bookmark',255,786)+iconButton('more',335,786)+r(146,853,108,4,C.ink,2);
let detected=iconButton('back',12,43,C.bg)+t(68,75,'Detected videos',21,700)+iconButton('close',336,43,C.bg)+t(24,126,'Pick your video',28,700)+t(24,151,'2 videos · fieldnotes.example',13,400,C.sub);
function choice(y,ph,title,dur,meta,selected){return r(24,y,352,242,C.white,22,selected?C.primary:C.line)+photo(ph,36,y+12,328,152,15)+badge(304,y+128,dur)+t(40,y+192,title,17,700)+t(40,y+217,meta,12,400,C.sub)+circ(344,y+200,11,selected?C.primary:C.white,selected?C.primary:C.line)+(selected?i('check',337,y+193,14,C.white):'');}
detected+=choice(178,'coast','Coastal escape','08:42','3 qualities · Video + audio',true)+choice(434,'mountain','Somewhere quieter','02:14','2 qualities · Video',false)+button(24,704,352,'Continue · 1 video','arrow')+t(200,785,'Preview first. Download the right one.',12,400,C.sub,'middle')+r(146,845,108,4,C.ink,2);
let quality=iconButton('back',12,44,C.bg)+t(68,76,'Coastal escape',20,700)+photo('coast',24,112,352,189,20)+badge(312,264,'08:42');
quality+=r(0,320,400,540,C.white,30)+r(174,334,52,5,C.line,3)+t(24,379,'Make it yours.',28,700)+t(24,405,'Choose the right quality for you.',14,400,C.sub)+chip(24,429,88,'Video',true)+chip(120,429,88,'Audio');
function opt(y,label,desc,size,selected,tag){return r(24,y,352,70,selected?C.soft:C.white,17,selected?C.primary:C.line)+circ(46,y+35,9,selected?C.primary:C.white,selected?C.primary:'#CDD2DF')+(selected?circ(46,y+35,3,C.white):'')+t(66,y+29,label,16,700)+t(66,y+51,desc,12,400,C.sub)+t(356,y+30,size,14,700,C.ink,'end')+(tag?t(356,y+51,tag,11,600,C.primary,'end'):'');}
quality+=opt(484,'1080p','Full HD · MP4','82 MB',true,'Best quality')+opt(564,'720p','HD · MP4','44 MB',false,'Smaller file')+opt(644,'480p','SD · MP4','25 MB',false,'Save data')+button(24,736,352,'Download · 82 MB','download')+t(200,818,'Save to Downloads · Change',12,500,C.sub,'middle')+r(146,845,108,4,C.ink,2);
let body='';
const rows=[['01','Instant capture','One clear action. No crowded dashboard.',home,'Home'],['02','Browser discovery','Keep the source visible while finding media.',browser,'Browser'],['03','Select your media','Preview, duration and source before download.',detected,'Detected'],['04','Quality selection','Clear quality / size trade-off before saving.',quality,'Quality']];
rows.forEach((a,k)=>{let x=48+k*448;body+=caption(x,213,a[0],a[1],a[2])+screen(a[4],x,250,a[3]);});
body+=r(48,1150,1776,90,C.white,20)+t(72,1182,'WHY THIS DIRECTION',11,700,C.primary)+t(72,1210,'Capture → Choose → Download → Enjoy',18,700)+t(600,1184,'Learned from the category',15,700)+t(600,1210,'Paste + browser entry • visible detection • quality + size • offline media',14,400,C.sub)+t(1798,1210,'Independent of the existing codebase',12,500,C.sub,'end');
writeBoard('v3-01-capture',board('Save something worth keeping.','A media-first Android downloader. Familiar task flows, clearer decisions, a fresher visual language.',body));
