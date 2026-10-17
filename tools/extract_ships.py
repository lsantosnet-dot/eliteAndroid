# Gera core/.../ships/OriginalShips.kt a partir do elite-source.asm de Mark Moxon:
#   curl -O https://raw.githubusercontent.com/markmoxon/elite-source-code-bbc-micro-cassette/main/1-source-files/main-sources/elite-source.asm
#   python3 tools/extract_ships.py elite-source.asm core/src/main/kotlin/com/eliteandroid/ships/OriginalShips.kt
import re, sys
src = open(sys.argv[1]).read().split('\n')
ships = ['SIDEWINDER','VIPER','MAMBA','PYTHON','MISSILE','ASTEROID','CANISTER','ESCAPE_POD','COBRA_MK_3','CORIOLIS']
out = []
def block(name):
    start = src.index('.SHIP_'+name)
    lines = []
    for l in src[start+1:]:
        if l.startswith('.SHIP_') and not l.startswith('.SHIP_'+name+'_'): break
        if l.startswith('\\       Name:'): break
        lines.append(l)
    return lines
def num(expr):
    return eval(expr.replace('%','0b'))
for name in ships:
    L = block(name)
    hdr = {}
    for l in L:
        m = re.match(r'\s*EQU[BW]\s+([^\\]+?)\s*\\\s*(.+?)\s*=\s*(.+)$', l)
        if m: hdr[m.group(2).strip()] = m.group(1).strip()
        m2 = re.match(r'\s*\\\s*Missiles\s*=\s*(\d+)', l)
        if m2: hdr['Missiles'] = m2.group(1)
    v = [list(map(int, re.findall(r'-?\d+', l.split('\\')[0].replace('VERTEX','')))) for l in L if l.strip().startswith('VERTEX')]
    e = [list(map(int, re.findall(r'-?\d+', l.split('\\')[0].replace('EDGE','')))) for l in L if l.strip().startswith('EDGE')]
    byte19 = None
    for l in L:
        if 'Laser power' in l:
            byte19 = num(l.split()[1])
    info = dict(canisters=num(hdr['Max. canisters on demise'].split()[0]) if 'Max. canisters on demise' in hdr else 0,
        area=num(hdr['Targetable area']), gun=num(hdr['Gun vertex'])//4,
        expl=(num(hdr['Explosion count'])-6)//4, bounty=num(hdr['Bounty']), vis=num(hdr['Visibility distance']),
        energy=num(hdr['Max. energy']), speed=num(hdr['Max. speed']), byte19=byte19)
    print(name, info, len(v), len(e), file=sys.stderr)
    out.append((name, info, v, e))
kt = ['package com.eliteandroid.ships','','/**',' * Modelos gerados a partir de elite-source.asm (disassembly de Mark Moxon, versão cassete)',' * pelo script de extração: cabeçalho (bytes do bloco do modelo), VERTEX e EDGE na ordem original.',' * Não edite à mão.',' */','object OriginalShips {']
for name, info, v, e in out:
    if name in ('COBRA_MK_3','CORIOLIS'): continue
    kt.append(f'    val {name}: ShipBlueprint = ShipBlueprint.define("{name.replace("_"," ").title()}") {{')
    kt.append(f'        stats(energy = {info["energy"]}, speed = {info["speed"]}, laserByte = {info["byte19"]}, bounty = {info["bounty"]}, canisters = {info["canisters"]}, gunVertex = {info["gun"]}, explosionVertices = {info["expl"]})')
    kt.append(f'        visibilityDistance = {info["vis"]}')
    kt.append(f'        targetableArea = {info["area"]}')
    for i,(x,y,z,*rest) in enumerate(v): kt.append(f'        vertex({x}, {y}, {z}) // {i}')
    for i,(a,b,f1,f2,vis) in enumerate(e): kt.append(f'        edge({a}, {b}, {f1}, {f2}, {vis}) // {i}')
    kt.append('    }'); kt.append('')
kt.append('}')
open(sys.argv[2],'w').write('\n'.join(kt)+'\n')
