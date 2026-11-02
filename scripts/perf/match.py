import sys, xml.etree.ElementTree as ET, re
# argv: <xml-file> <what: card|play|pause>
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    sys.exit(0)
for n in root.iter():
    b = n.get('bounds')
    if not b:
        continue
    if sys.argv[2] == 'card' and n.get('clickable') != 'true':
        continue
    if sys.argv[2] == 'card' and any('Memory of Trees' in (m.get('text') or '') for m in n.iter()):
        m = re.findall(r'\d+', b); print((int(m[0])+int(m[2]))//2, (int(m[1])+int(m[3]))//2); break
    if sys.argv[2] in ('play', 'pause') and (n.get('content-desc') == sys.argv[2].capitalize() or n.get('text') == sys.argv[2].capitalize()):
        m = re.findall(r'\d+', b); print((int(m[0])+int(m[2]))//2, (int(m[1])+int(m[3]))//2); break
