# Gera core/.../ships/DiscShips.kt a partir dos 16 arquivos de naves da versão em disco do
# BBC Elite (disassembly de Mark Moxon, repositório disc-elite-beebasm):
#   for x in a b c d e f g h i j k l m n o p; do curl -O \
#     https://raw.githubusercontent.com/markmoxon/disc-elite-beebasm/master/1-source-files/main-sources/elite-ships-$x.asm; done
#   python3 tools/extract_disc_ships.py <pasta com elite-ships-*.asm> core/src/main/kotlin/com/eliteandroid/ships/DiscShips.kt
#
# Extrai os modelos (VERTEX/EDGE/FACE e o cabeçalho), a tabela XX21 de cada arquivo (quais
# tipos de nave ele contém) e a tabela E% (flags NEWB padrão de cada tipo).
import os, re, sys

folder, out_path = sys.argv[1], sys.argv[2]
LETTERS = 'abcdefghijklmnop'
files = {x: open(os.path.join(folder, f'elite-ships-{x}.asm')).read().split('\n') for x in LETTERS}

def num(expr):
    expr = expr.replace('%', '0b')
    return eval(expr)

def block(src, name):
    start = src.index('.SHIP_' + name)
    lines = []
    for l in src[start + 1:]:
        if l.startswith('.SHIP_') and not l.startswith('.SHIP_' + name + '_'): break
        if l.startswith('\\       Name:'): break
        lines.append(l)
    return lines

def rows(lines, kw):
    return [list(map(int, re.findall(r'-?\d+', l.split('\\')[0].replace(kw, ''))))
            for l in lines if l.strip().startswith(kw + ' ')]

blueprints = {}   # nome -> dados
ship_files = {}   # letra -> {slot: nome}
newb = {}         # slot -> flags

for x in LETTERS:
    src = files[x]
    i = src.index('.XX21')
    slots = {}
    slot = 1
    for l in src[i + 1:]:
        if not l.strip().startswith('EQUW'):
            if slots or slot > 1: break
            continue
        m = re.search(r'EQUW\s+SHIP_(\w+)', l)
        if m: slots[slot] = m.group(1)
        slot += 1
    ship_files[x] = slots
    i = src.index('.E%')
    slot = 1
    for l in src[i + 1:]:
        if not l.strip().startswith('EQUB'):
            if slot > 1: break
            continue
        v = num(l.split()[1])
        if slot in slots: newb.setdefault(slot, v)
        slot += 1
    for name in slots.values():
        # O míssil fica no código principal (é o mesmo da versão cassete).
        if name in blueprints or '.SHIP_' + name not in src: continue
        L = block(src, name)
        equ = [l for l in L if re.match(r'\s*EQU[BW]\s', l)]
        def hdr(label):
            for l in equ:
                if label in l: return re.match(r'\s*EQU[BW]\s+([^\\]+?)\s*\\', l).group(1)
            raise KeyError(label)
        byte0 = num(hdr('Max. canisters on demise'))
        v = rows(L, 'VERTEX')
        e = rows(L, 'EDGE')
        if not e:
            other = next(re.search(r'SHIP_(\w+?)_EDGES', l).group(1) for l in L if 'Edges from' in l)
            e = rows(block(src, other), 'EDGE')
        f = rows(L, 'FACE')
        if not f:
            other = next((re.search(r'SHIP_(\w+?)_FACES', l).group(1) for l in L if 'Faces from' in l), None)
            if other: f = rows(block(src, other), 'FACE')
        blueprints[name] = dict(
            canisters=byte0 & 15, cargo=byte0 >> 4,
            area=num(hdr('Targetable area')), gun=num(hdr('Gun vertex')) // 4,
            expl=(num(hdr('Explosion count')) - 6) // 4, bounty=num(hdr('Bounty')),
            vis=num(hdr('Visibility distance')), energy=num(hdr('Max. energy')),
            speed=num(hdr('Max. speed')), byte19=num(hdr('Laser power')),
            scale=num(hdr('Normals are scaled by')), v=v, e=e, f=f)

def kname(n): return n
NAMES = {'ASP_MK_2': 'Asp Mk II', 'COBRA_MK_1': 'Cobra Mk I', 'COBRA_MK_3': 'Cobra Mk III',
         'COBRA_MK_3_P': 'Cobra Mk III (pirate)', 'FER_DE_LANCE': 'Fer-de-Lance', 'PYTHON_P': 'Python (pirate)',
         'PLATE': 'Alloy Plate', 'DODO': 'Dodecahedron', 'CORIOLIS': 'Coriolis'}

def title(n):
    if n in NAMES: return NAMES[n]
    pirate = n.endswith('_P')
    t = (n[:-2] if pirate else n).replace('_', ' ').title().replace(' Mk ', ' Mk ')
    return t + ' (pirate)' if pirate else t

kt = ['package com.eliteandroid.ships', '',
      '/**',
      ' * Modelos da versão em disco do BBC Elite, gerados a partir dos arquivos de naves',
      ' * elite-ships-a..p.asm (disassembly de Mark Moxon) por tools/extract_disc_ships.py:',
      ' * cabeçalho, VERTEX, EDGE e FACE (normais do original) na ordem original.',
      ' * Não edite à mão.',
      ' */',
      'object DiscShips {']
for name in sorted(blueprints):
    b = blueprints[name]
    kt.append(f'    val {kname(name)}: ShipBlueprint = ShipBlueprint.define("{title(name)}") {{')
    kt.append(f'        stats(energy = {b["energy"]}, speed = {b["speed"]}, laserByte = {b["byte19"]}, bounty = {b["bounty"]}, '
              f'canisters = {b["canisters"]}, gunVertex = {b["gun"]}, explosionVertices = {b["expl"]}, cargoItem = {b["cargo"]})')
    kt.append(f'        visibilityDistance = {b["vis"]}')
    kt.append(f'        targetableArea = {b["area"]}')
    kt.append(f'        normalScale = {b["scale"]}')
    for i, (x, y, z, *_) in enumerate(b['v']): kt.append(f'        vertex({x}, {y}, {z}) // {i}')
    for i, (a, c, f1, f2, vis) in enumerate(b['e']): kt.append(f'        edge({a}, {c}, {f1}, {f2}, {vis}) // {i}')
    for i, (nx, ny, nz, vis, *_) in enumerate(b['f']): kt.append(f'        face({nx}, {ny}, {nz}, {vis}) // {i}')
    kt.append('    }')
    kt.append('')
kt.append('    /** XX21 de cada arquivo de naves (D.MOA … D.MOP): tipo (1..31) → modelo. */')
kt.append('    val SHIP_FILES: List<Map<Int, ShipBlueprint>> = listOf(')
for x in LETTERS:
    kt.append(f'        mapOf({", ".join(f"{s} to {kname(n)}" for s, n in sorted(ship_files[x].items()) if n in blueprints)}), // {x.upper()}')
kt.append('    )')
kt.append('')
kt.append('    /** E%: flags NEWB padrão de cada tipo de nave (1..31). */')
kt.append('    val NEWB: Map<Int, Int> = mapOf(' + ', '.join(f'{s} to 0b{v:08b}' for s, v in sorted(newb.items())) + ')')
kt.append('}')
open(out_path, 'w').write('\n'.join(kt) + '\n')
print(len(blueprints), 'modelos:', ', '.join(sorted(blueprints)), file=sys.stderr)
for s, v in sorted(newb.items()): print(s, f'{v:08b}', file=sys.stderr)
