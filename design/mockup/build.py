import re,sys,os
d=os.path.dirname(os.path.abspath(__file__))
r=lambda f:open(os.path.join(d,'src',f),encoding='utf-8').read()
fonts="https://fonts.googleapis.com/css2?family=Manrope:wght@200..800&amp;family=Inter:wght@100..900&amp;family=Onest:wght@100..900&amp;family=Golos+Text:wght@400..900&amp;family=Jost:wght@100..900&amp;family=Nunito:wght@200..1000&amp;family=Raleway:wght@100..900&amp;family=Source+Serif+4:wght@200..900&amp;display=swap"
head=f'''<title>Погодная среда</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="{fonts}">
<style>
{r('style.css')}
</style>
'''
body=r('body.html')
js=f"<script>\n{r('core.js')}\n{r('scene.js')}\n</script>\n<script>\n{r('app.js')}\n</script>\n"
page=head+body+js
open(os.path.join(d,'page.html'),'w',encoding='utf-8').write(page)
test='<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><style>:root{color-scheme:light}body{margin:0}</style></head><body>'+page+'</body></html>'
open(os.path.join(d,'index.html'),'w',encoding='utf-8').write(test)
print(len(page),'bytes')
