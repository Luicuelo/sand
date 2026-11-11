# El simulador de materiales: reglas y sistema

Documento de referencia del simulador. Explica **cómo está montado** y, sobre
todo, **por qué** cada pieza es como es: la mayoría de las decisiones de aquí
salieron de un fallo concreto, así que cada una viene con su motivo.

Si dentro de un mes algo no cuadra, el orden de lectura recomendado es: las
secciones 4 (la célula), 5 (la vida) y 7 (cómo se aplica una regla). Con esas
tres se puede leer cualquier regla de la tabla y saber qué hace.

---

## 1. La idea en una frase

Cada célula guarda **un número** (un índice de paleta) que dice a la vez *de qué
material es* y *cuánta vida le queda*. En cada paso, y solo cuando su reloj se lo
marca, cada partícula busca en una tabla la **primera regla que encaja**
con su vecindad y el resultado de esa regla dice qué se escribe en la generación
siguiente.

La regla es un par de dibujos de texto:

```
lo que hay ahora        lo que se escribe después
    ?F?                     ???
    ?M?          →          ?W?
    ???                     ???
```

Eso es madera con fuego encima: la madera envejece un paso. No hay código para
"la madera arde": hay un dibujo que lo dice.

---

## 2. Los cuatro archivos

| Archivo | Responsabilidad |
|---|---|
| [MaterialSimulator.java](src/principal/MaterialSimulator.java) | Ventana, barra de materiales, teclado, bucle de 60 FPS |
| [WorldRenderer.java](src/principal/WorldRenderer.java) | Doble búfer, paleta, pincel del ratón, golpes, el reloj de cada material y el del dibujo, pintado a píxeles |
| [MaterialRules.java](src/principal/MaterialRules.java) | Leer vecindades y aplicar las reglas: el motor, literalmente |
| [Material.java](src/principal/Material.java) | La tabla de materiales: glifo, banda de valores, vida, reloj y colores |

Toda la configuración vive en dos sitios: la lista `Material.LIST` (qué
materiales existen) y la tabla `Pattern.STEPS` (qué hacen). El resto del código
es maquinaria que no hay que tocar para añadir cosas.

Fuera de este paquete está `src/sand`, el motor original de arena recuperado del
commit `a2679ca` y sin tocar (`sand.Sand`). Compila en el mismo proyecto pero no
comparte nada con estos cuatro archivos: es la referencia de dónde venimos.

---

## 3. Un fotograma, paso a paso

`WorldRenderer.tick()` se ejecuta 60 veces por segundo, y cada llamada es **un
fotograma**: `ENGINE_MULTIPLIER` pasos del mundo (hoy 4) y, cuando le toca por su
cuenta, un pintado. El multiplicador se toca solo y acelera todo a la vez, porque
está por encima de los relojes de los materiales: no cambia ninguna regla.

1. **Golpe** (si hay uno activo): `pushParticles()` empuja lo que haya hacia un
   lado y el resultado pasa a ser la generación actual. El golpe se mide en
   fotogramas, que es lo que dura un gesto del ratón, así que el multiplicador no
   lo acelera.
2. **Pasos del mundo**, repetidos `ENGINE_MULTIPLIER` veces:
   - **Pincel**: una partícula del material seleccionado en la celda del ratón, o
     en un cuadrado de 2 celdas de radio si se mantiene SPACE. Va dentro del paso
     y con el reloj del material, pero al doble: suelta un grano cada dos turnos
     del material seleccionado, que son veinticuatro pasos para la arena y dos
     para el agua (ver "El reloj de cada material").
   - **Barrido**: se recorre la rejilla fila por fila y, en cada celda no vacía,
     se pregunta antes a su material si le toca en este paso
     (`Material.isDue`). Si le toca, se llama a `MaterialRules.stepParticle()`, que
     aplica la primera regla que encaja y escribe en el búfer siguiente; si no le
     toca, la celda se copia tal cual al búfer siguiente.
   - **Intercambio**: `advanceGeneration()` cambia `cells` por `nextCells` y vacía
     el que queda libre. Nunca se copian datos: se intercambian las referencias.
3. **Pintado** (`FRAMES_PER_PAINT = 1`): la imagen se rehace en todos los
   fotogramas. Con varios pasos por fotograma el mundo se movía varias celdas
   entre imagen e imagen y se notaba el salto, así que el pintado va a la par del
   fotograma y no del paso. La rejilla se expande a bloques de 8x8 píxeles en un
   `byte[]` BGRA y se manda a la imagen en una sola llamada.

### El reloj de cada material

El renderer lleva dos contadores y cada uno tiene su trabajo. `frameCounter`
cuenta **fotogramas** y lo dividen el golpe y el pintado; `stepCounter` cuenta
**pasos del mundo** y lo dividen los materiales y el pincel. Con el multiplicador
en 4 hay cuatro pasos por fotograma, es decir, 240 pasos por segundo.

Cada material guarda cada cuántos pasos da uno de los suyos: es el número que va
después del `spread` en `Material.LIST`. El agua va con 1 y da un paso en todos los
pasos (240 por segundo); la arena va con 6 y da uno de cada seis (40 por segundo),
así que cae y se abre a un sexto de la velocidad del agua. La madera, el fuego y el
humo van con 3 porque sus vidas están medidas en pasos: dan 80 por segundo, y su
banda sigue contando filas, porque cada turno que dan es una fila que suben o un
paso de vida que gastan.

La partícula que no tiene el turno **no se omite del todo**: su celda se copia al
búfer siguiente. No es un extra, es obligatorio, porque el barrido escribe en el
otro búfer y la que no se copiara desaparecería. Copiada, conserva su sitio, su
color y su vida hasta que le vuelva a tocar.

**El pincel usa el mismo reloj, al doble.** `dropUnderMouse()` se llama dentro del
paso y suelta un grano cada **dos** turnos del material seleccionado
(`stepCounter % (stepPeriod * 2)`): uno cada veinticuatro pasos para la arena (una
vez cada seis fotogramas) y uno cada dos para el agua (dos por fotograma). Dos
turnos y no uno porque un grano caído en el mismo turno en que nace aparece en la
celda que el anterior acaba de dejar, y el `match` mira la generación anterior:
todavía lo ve ahí, no cae y se desliza en diagonal. Con un turno de hueco en medio
siempre nace sobre celda libre y el chorro baja recto. Es el mismo truco que hacía
`DROP_EVERY_FRAMES = 2` antes de que existieran los relojes, pero atado al material
en vez del fotograma: medido, el chorro pasa de 3 celdas de ancho a 1.

**El barrido alterna por fotograma, no por paso.** El sentido del barrido es lo que
reparte la ventaja cuando dos partículas quieren la misma celda, y solo sirve
alternando *entre turnos de la misma partícula*. Como los turnos de un material se
cuentan en pasos y el sentido se cambia por fotograma, lo que hay que mirar es en
qué fotogramas caen sus turnos: la arena da uno cada 6 pasos y un fotograma tiene 4,
así que sus turnos caen en los fotogramas 0, 1, 3, 4, 6… y ven los dos sentidos. Los
materiales más rápidos que el fotograma (agua con 1, madera, fuego y humo con 3) se
evalúan varias veces por fotograma y acaban viendo los dos sentidos de todas formas.
Si el cociente `reloj / multiplicador` fuera entero y par, todos los turnos caerían
del mismo lado: medido con la arena en 12 y el multiplicador en 2 (cociente 6), el
bloque se iba 5,4 celdas a un lado y se aplanaba (13 de alto y 42 de ancho, frente a
17 y 37). Con el reloj de hoy, un montón de 200 granos soltado sobre un charco de
800 gotas acaba a 0,06 celdas del centro.

### Dos detalles del barrido que importan

- **Doble búfer**: `cells` es lo que se lee, `nextCells` lo que se escribe. Nadie
  mira `nextCells` salvo para comprobar si una celda sigue libre (`isFree`).
  Gracias a eso una partícula no puede "verse" a sí misma dos veces.
- **El sentido del barrido se alterna** cada fotograma (`frameCounter & 1`).
  Cuando dos partículas quieren la misma celda, gana la que se lee primero; con
  un sentido fijo, la misma partícula ganaría siempre y las pilas crecerían
  torcidas hacia un lado. Alternando, la simetría se conserva (comprobado: el
  centroide de un montón se queda a menos de una celda del centro).

---

## 4. La célula: un solo `int`

`cells[y][x]` es un índice de la paleta de 256 colores:

| Valor | Significado |
|---|---|
| `0` | vacío (se dibuja blanco) |
| `1` a `191` | un material, con su vida |
| resto | sin uso |

Que el material y la vida sean **el mismo número** es la decisión central del
diseño: no hay un array de materiales al lado del de vidas que se puedan
desincronizar. Si una partícula envejece, cambia de color; si cambia de color,
es que ha envejecido.

Consecuencia inmediata: **el 0 no puede ser un material**, porque 0 es vacío en
todo el motor. Por eso las bandas empiezan en 1 (y la idea original de "arena de
0 a 10" no era viable).

`Material.glyphOf[256]` es la tabla inversa (valor → glifo) que consulta el
`match`: es lo que permite escribir `F` en un patrón y que coincida con
cualquiera de los 30 valores del fuego.

### Los materiales

| Glifo | Material | Valores | Vida | Nace en | Reloj | Rampa |
|---|---|---|---|---|---|---|
| `S` | arena | 1-25 | no | 1-25 | 6 | amarillo pálido → ocre dorado |
| `A` | agua | 26-45 | no | 26-45 | 1 | azul claro → azul profundo |
| `M` | madera | 46-75 | sí (30 pasos) | 46-54 | 3 | madera clara → madera oscura |
| `F` | fuego | 76-105 | sí (30 pasos) | 76-81 | 3 | amarillo pálido → naranja → rojo |
| `H` | humo | 106-185 | sí (80 pasos) | 106-111 | 3 | gris oscuro → gris claro |
| `N` | agua que sujeta un grano | 186-191 | no (la banda es la espera) | 186 | 6 | azul agua, un punto más oscuro |

Las bandas se asignan solas en el orden de la lista, desde 1. Añadir un material
es añadir una línea; los valores se recalculan.

`N` no es un material que se pinte: es el **estado** de la gota que tiene un grano
de arena encima y está esperando para cambiarse de sitio con él (§8). Su banda no
es una vida, es la espera: seis valores que se recorren en cinco turnos con el
`W`, más el hueco hasta el siguiente turno del grano, que es lo que da los **nueve
fotogramas por fila** medidos (0,15 s, 6,7 filas por segundo, frente a 40 en el
aire). Los colores son los del agua un poco más oscuros, para que se note algo y el
charco no cambie de color. Como no tiene vida, no se pinta el fantasma blanco del
último valor y la espera no parpadea.

El ancho de cada banda no es decorativo: **es la vida**, y subir cuesta un paso
por fila. El lienzo por defecto mide 75 filas, así que el fuego, que se apaga a
un tercio de pantalla, se queda con 30 pasos; el humo, que es lo que tiene que
subir hasta el techo, necesita 80 (75 filas más el margen del nacimiento). La
madera se queda con 30 porque su vida es lo que tarda en arder, no lo que sube.
El reloj no cambia esa cuenta: el fuego y el humo llevan reloj 3, así que dan un
paso cada tres pasos del mundo y sus 30 u 80 turnos siguen siendo 30 u 80 filas. La
arena, en cambio, con reloj 6 baja una fila cada seis pasos, es decir, una cada
fotograma y medio con el multiplicador en 4.

---

## 5. La vida es la posición en la banda

Para un material con vida, la posición dentro de su banda **es** su edad. Cuatro
glifos la mueven, y solo cuatro:

| Glifo | Efecto | Para qué |
|---|---|---|
| `V` | +1, y al llegar al final vuelve al principio | Ciclos sin fin: es lo que reinicia la espera de la gota que sujeta un grano (§8) |
| `W` | +1, y al llegar al final **se queda ahí** | Envejecer hasta morir |
| `r` | Se salta a la mitad de la vida que le **queda** | Quemar deprisa |
| `l` | Retrocede a la mitad de la vida que ya **gastó** | Rejuvenecer / alimentar |

Detalles que no son obvios:

- `W` **no escribe 0** al final: se queda en el último valor. Morir nunca es un
  efecto colateral de moverse; morir es siempre una regla escrita (`f → H`). Eso
  es lo que hace que la transformación sea explícita y legible.
- `r` y `l` siempre se mueven **al menos un paso** (`max(1, ...)`), aunque la
  mitad salga 0. Si no, un valor pegado al borde se quedaría inmóvil para
  siempre.
- **El último valor de un material con vida se pinta casi blanco** (85 % hacia
  blanco, en `WorldRenderer.Palette`). Es un **fantasma**: el material sigue ahí,
  invisible, esperando la regla que lo transforme. Por eso la madera parece
  desaparecer justo antes de arder.
- Ningún material nace con un valor fijo: `spawn()` sortea entre los primeros
  valores de su banda (`spread`), para que un montón no sea una plancha de un
  solo color. Sin vida eso solo cambia el tono. Con vida el valor **es** la edad,
  así que el sorteo es también vida gastada al nacer, y por eso el margen se
  queda corto y distinto en cada material: la madera sortea entre 9 valores de 30
  (y por eso arde un poco antes), el fuego entre 6 de 30, y el humo entre 6 de 80,
  que es justo lo que se puede gastar sin dejar de llegar al techo.

### La cadena completa de una pared de madera

```
madera  M (46..75)  --W-->  m (75, fantasma)
                            --WOOD_END-->  F (76, fuego nuevo)
                                           --W-->  f (105, fantasma)
                                                  --FIRE_END-->  H (humo, 106)
                                                                 --W-->  h (185, fantasma)
                                                                        --SMOKE_END-->  vacío
```

Cada flecha es una regla de la tabla. Ninguna transición está escondida en el
código.

---

## 6. El vocabulario de glifos

El mismo carácter significa cosas distintas según el dibujo en el que esté, y
esa es la clave del sistema: **un glifo es una condición en el `match` y una
orden de escritura en el `result`**.

### En el `match` (la condición)

| Glifo | Encaja con |
|---|---|
| `?` | cualquier cosa (ni se evalúa) |
| `X` | cualquier partícula; los bordes laterales e inferior cuentan como partícula |
| `_` | hueco; **por encima de la rejilla todo cuenta como hueco** |
| `S` `A` `M` `F` `H` `N` | cualquier valor de ese material |
| `s` `a` `m` `f` `h` `n` | **solo el último valor** de ese material: existe pero ya no tiene vida (para la marca `n` es el que dice que ya ha esperado bastante) |

### En el `result` (la escritura)

| Glifo | Qué escribe |
|---|---|
| `?` | nada (deja la celda como esté) |
| `_` | vacío |
| `X` | la partícula, con su valor exacto |
| `S` `A` `M` `F` `H` `N` | una partícula **nueva** de ese material, con `spawn()` |
| `x` | la partícula, sorteada entre todas las `x` del resultado |
| `o` | una partícula **nueva** con una parte de la vida que le queda a la partícula |
| `V` `W` `r` `l` | la partícula, con su vida avanzada o retrocedida |

### `x` frente a `o`

Es la diferencia entre **mover** y **crear**:

- `x` (compartido): solo **una** de las celdas se queda la partícula, así que
  nada se duplica. Las dos diagonales libres se sortean al 50 %. Si ninguna gana
  el dado, la reserva garantiza que la última se la quede: **la partícula nunca
  se pierde**.
- `o` (repetido): cada celda tira **su propio** dado, y pueden ganar varias. Es
  el único glifo que crea de la nada, y por eso solo escribe en celdas vacías.
  La vida que le queda a la partícula **se reparte entre las copias**
  (`lifeOfCopy`): con cuatro `o`, cada hijo se lleva un cuarto de lo que le
  quedaba al padre, y cuando ya no queda nada que repartir **no nace nadie**. Así
  un hijo nunca vive más que su padre.
  En el fuego se usa con cuatro `o` (una de cada cuatro posibilidades por celda):
  media **un** descendiente por generación. Solo con eso estaríamos en el punto
  crítico —ni crece ni se apaga—, pero como cada relevo se lleva un cuarto de la
  vida que queda, la estirpe pierde vida en cada generación y el fuego acaba
  apagándose.

---

## 7. Cómo se aplica una regla

Dos pasos: comprobar (`matches`) y escribir (`applyPattern`).

### 7.1 Comprobar una vecindad

`matches(Step, ...)` recorre el dibujo del `match` y compara glifo a glifo,
**cortando en el primer fallo**. `?` se salta sin comparar.

El ancla se calcula: `anchorX = ancho / 2`, `anchorY = alto / 2`. El centro de un
dibujo es su carácter central, así que **el patrón puede ser de cualquier tamaño
impar** y nadie tiene que decir dónde está el centro. De ahí que convivan reglas
de 1x1 (`M → X`), de 1x3 (`?H? → ?W?`) y de 3x3.

`glyphAt(x, y, cells)` es quien define el mundo:

```java
if (x < 0 || x >= cols || y >= rows) return PARTICLE;   // lados y suelo: sólidos
if (y < 0) return EMPTY;                                // arriba: aire
return Material.glyphOf[cells[y][x]];
```

Eso es **todo** el borde del simulador: dos líneas. Y tiene un efecto bonito: una
llama que intenta subir desde la fila de arriba no encuentra celda donde
escribirse, así que se apaga sola al salir por el techo, sin ningún caso
especial.

### 7.2 Escribir el resultado

`applyPattern` es la parte fina. **Durante el recorrido no escribe la
partícula**: solo decide. Se decide con tres datos:

```java
boolean claimed;            // ¿algún glifo se lleva la partícula?
int destinationX, destinationY, destinationValue;   // dónde y con qué valor
boolean originWritten;      // ¿la regla hizo algo en la celda de la partícula?
```

El valor va separado de las coordenadas porque `V`, `W`, `r` y `l` **mueven y
envejecen a la vez**: el destino no es "la partícula", es "la partícula con otro
valor".

Y al final hay exactamente cuatro salidas posibles:

1. **Alguien la reclamó** y el destino está libre → se escribe en el destino. Mover.
2. **Alguien la reclamó** pero otra partícula llegó antes a esa celda → vuelve a
   su sitio. Nunca se pierde nada.
3. **Nadie la reclamó** y la regla no tocó su celda → se queda donde estaba. Es
   el "no pasa nada".
4. **Nadie la reclamó** y la regla escribió otra cosa en su celda (un `_` o una
   letra) → la partícula **se consume**. Esa es la forma de transformar un
   material: `m → F` significa "vacía esta celda de madera agotada y pon fuego".

Si el destino está dentro del dibujo pero fuera de la rejilla, el `continue` lo
ignora: escribir "fuera del mundo" no es un error, simplemente no ocurre.

### 7.3 Qué pasa si dos partículas quieren la misma celda

Las escribe la que llegue primero en el barrido; la segunda se encuentra la celda
ocupada y vuelve a su sitio (salida 2). Por eso el sentido del barrido se
alterna: para que "llegar primero" no signifique siempre lo mismo.

---

## 8. Las reglas, por grupos

La tabla `Pattern.STEPS` se lee como una lista de prioridad: **gana la primera
que encaja**. El orden es deliberado y está explicado al final de esta sección.

### Agua contra fuego

Las ocho primeras de la tabla (las llamas que se apagan) y las tres últimas
más cercanas a la gravedad (las gotas que saltan):

| Regla | Dibujo | Qué cuenta |
|---|---|---|
| `FIRE_DIES_*` (ocho, las primeras) | `?A? / ?F? / ???` → `?_?` | Una llama con agua en **cualquiera** de sus ocho lados se apaga: se consume en el sitio en vez de subir o propagarse |
| `WATER_PUTS_OUT_ABOVE` | `?F? / ?A? / ???` → `?X? / ?_? / ???` | La gota salta a la celda que la llama acaba de dejar |
| `WATER_PUTS_OUT_UPPER_LEFT` | `F?? / ?A? / ???` → `X?? / ?_? / ???` | Lo mismo cuando la llama está en la diagonal izquierda |
| `WATER_PUTS_OUT_UPPER_RIGHT` | `??F / ?A? / ???` → `??X / ?_? / ???` | Y en la derecha |

Son ocho dibujos para una sola idea porque **un `match` dice qué hay en cada
celda y no sabe decir "agua en cualquier sitio"**: la misma regla hay que verla
desde los ocho lados.

Y es el fuego quien se apaga, no el agua quien lo mata: **una partícula siempre
se escribe a sí misma al final de su paso** (salida 3 de §7.2), así que solo la
llama puede decidir no volver. Si el agua escribiera su celda, el fuego llegaría
después y escribiría encima, borrando la gota.

El salto de la gota va **solo hacia arriba**, y no por capricho: el barrido sube
fila a fila, así que la llama de encima ya está apagada cuando se mira el agua y
la celda está libre. Hacia abajo o de lado se movería la gota primero y la llama
la borraría al escribir su propio valor. Los huecos de esos lados no necesitan
regla: el agua los rellena cayendo y fluyendo como cualquier otro.

Y por eso el salto va al final del grupo y no al principio: el centro de esas tres
reglas es `A`, así que solo las mira el agua, y su único requisito es pasar antes
de `FREE_FALL` y de `STAY`. Si fueran después de la gravedad, la gota caería en
vez de saltar al hueco que ha dejado la llama.

### Finales de vida

| Regla | Dibujo | Qué cuenta |
|---|---|---|
| `FIRE_END` | `f → H` | Una llama agotada se convierte en humo, no desaparece |
| `SMOKE_END` | `h → _` | Lo único que se va a la nada es el humo agotado |
| `WOOD_END` | `m → F` | Madera agotada prende: eslabón entre la madera y el fuego |

### Madera

| Regla | Dibujo | Qué cuenta |
|---|---|---|
| `WOOD_BURNS_ABOVE` | `?F? / ?M? / ???` → `?W?` | Madera con fuego encima envejece |
| `WOOD_BURNS_BELOW` | `??? / ?M? / ?F?` → `?W?` | Ídem con fuego debajo |
| `WOOD_BURNS_LEFT` | `??? / FM? / ???` → `?W?` | Ídem con fuego a la izquierda |
| `WOOD_BURNS_RIGHT` | `??? / ?MF / ???` → `?W?` | Ídem con fuego a la derecha |
| `FIRE_FEEDS` | `??? / ?F? / ?M?` → `?l?` | Llama con leña debajo se rejuvenece y se queda quieta |
| `WOOD_STAYS` | `M → X` | La madera no cae: es el único material que se queda donde lo pones |

Cuatro reglas para "arder" porque **un patrón solo puede modificar su propia
partícula**: el envejecimiento se escribe desde la madera mirando hacia donde
está el fuego, no desde el fuego hacia la madera.

`WOOD_STAYS` escribe `X` (la partícula) y **no** `M` (el material): con `M` la
madera renacería con la vida a tope en cada generación y no se gastaría nunca.

### Fuego y humo

| Regla | Dibujo | Qué cuenta |
|---|---|---|
| `FIRE_RISES` | `?_? / ?F? / ???` → `?W? / ?_? / ???` | Con hueco encima sube una fila **y envejece** |
| `FIRE_SPREADS` | `??? / ?F? / ???` → `?o? / oWo / ?o?` | Si no puede subir, se propaga a las cuatro celdas de al lado, repartiendo entre ellas la vida que le queda |
| `SMOKE_RISES` | `?_? / ?H? / ???` → `?W? / ?_? / ???` | El humo sube igual, pero sin propagarse |
| `SMOKE_AGES` | `?H? → ?W?` | Humo que no puede subir envejece donde está |

`SMOKE_AGES` parece redundante y no lo es: es la regla que garantiza que **todo
material con vida envejezca aunque esté atrapado**. Sin ella, el humo de un
fuego que ardía bajo la arena no subía, no llegaba nunca a su último valor y se
quedaba para siempre como un grano gris sujetando la arena.

### Agua

| Regla | Dibujo | Qué cuenta |
|---|---|---|
| `WATER_FLOWS_BOTH` | `_A_ / ?X?` → `x_x` | Con hueco en los dos lados, se desliza a uno de ellos |
| `WATER_FLOWS_LEFT` | `_AX / ?X?` → `X_?` | Solo el lado izquierdo libre |
| `WATER_FLOWS_RIGHT` | `XA_ / ?X?` → `?_X` | Solo el lado derecho libre |

Esto es lo que hace que el agua **nivele** en vez de apilarse en pirámide como la
arena. Usa `x` (compartido) y no dos `X`: el agua se reparte, no se duplica. Y
como los lados de la condición son `_`, el destino está garantizado libre.

La fila de abajo solo pide **su propia celda** (`?X?`) y no las tres (`XXX`): lo
de caer ya lo ha resuelto `FREE_FALL`, que va antes, así que cuando se llega aquí
el hueco de debajo no existe; pedir además las dos diagonales solo servía para
bloquear la nivelación en cuanto el lecho tenía una irregularidad, que es justo
donde un líquido más tiene que nivelar.

Van **después** de `FREE_FALL` (si hay hueco justo debajo, la gota cae) y
**antes** de los deslizamientos diagonales: un líquido no tiene ángulo de
reposo, así que una gota que ha aterrizado se abre de lado y solo rueda por una
pendiente cuando ya no le queda hueco lateral. Con las diagonales primero se
apila en el mismo cono que la arena.

Y **antes** de `STAY` (si fueran después, el agua se apilaría y nunca llegaría a
ellas).

### La gota que sujeta un grano: el hundimiento lento

Cuando un grano cae sobre agua no se hunde: se queda encima, y la gota que lo
sostiene pasa a ser una **marca** (`N`) que cuenta la espera. Cuando la espera
termina, los dos **cambian de sitio**: el grano baja una fila y la gota sube una.

```
1. la gota se marca               2. la marca envejece
?S?      ???                      ?S?      ???
?A?  ->  ?N?                      ?N?  ->  ?W?
???      ???                      ???      ???

3. el grano baja                 4. la marca sube y reinicia la espera
???      ???                      ?S?      ?V?
?S?  ->  ?_?                      ?n?  ->  ???
?n?      ?X?                      ???      ???

5. la marca sin grano encima vuelve a ser agua:  ?N? -> ?A?
```

El cambio de sitio se escribe con **dos reglas**, una desde cada partícula, porque
una regla solo puede escribir su propia partícula (§7.2): el grano no puede mover a
la gota ni la gota al grano. Los dos dibujos están calculados para que **no se
pisen**: el 3 vacía la celda del grano, para que la gota tenga dónde subir, y el 4
no escribe nada en la suya, para no borrar el grano que acaba de caer ahí. Funciona
porque las filas se barren **de arriba abajo**: el grano se mira siempre antes y la
celda que deja ya está vacía cuando la gota la pide. Con el orden al revés, o con
un `_` en el centro del dibujo 4, uno de los dos desaparecía en cada cambio.

La marca sube con `V` y no con `X`: al subir **vuelve al primer valor de su banda**.
Sin eso la espera se pagaría una sola vez, porque a partir del segundo grano la
marca ya estaría en el último valor y cada grano se hundiría en un paso, al ritmo
del aire. Medido: de 9 fotogramas por fila a 1,5.

La de subir va **antes** que la de envejecer aunque parezca lo contrario: `?N?`
encaja con toda la banda, el último valor incluido, así que si envejeciera primero
la marca de una gota que ya ha esperado no subiría nunca y el grano se quedaría
para siempre encima. Y las cuatro van antes de `FREE_FALL`, junto al agua, porque
la marca es un estado del agua y no un material aparte: una gota marcada que pierde
su grano vuelve a ser agua antes de que le toque moverse.

Lo que cuesta: cada grano **congela** la gota que tiene debajo mientras espera. Una
columna de 200 granos sobre un charco de 800 gotas llega a tener 217 marcas a la
vez en su peor momento, y poco después ninguna: todas se sueltan. Medido en 900
fotogramas, arena 200 → 200, gotas 800 → 800, cero marcas al final y el nivel del
charco cinco filas más alto, que es justo el volumen de los granos desalojados. El
fuego, en cambio, todavía no reconoce `N`: las ocho reglas de `FIRE_DIES_*` piden
`A`, así que una llama al lado de una gota marcada no ve el agua.

### Gravedad (sirve para todos los materiales)

| Regla | Dibujo | Qué cuenta |
|---|---|---|
| `FREE_FALL` | `??? / ?X? / ?_?` → `?X?` abajo | Cae una fila |
| `SLIDE_BOTH` | `??? / ?X? / _X_` → `x?x` | Algo debajo, las dos diagonales libres: una de las dos |
| `SLIDE_LEFT` | `??? / ?X? / _XX` → `X??` | Solo la diagonal izquierda |
| `SLIDE_RIGHT` | `??? / ?X? / XX_` → `??X` | Solo la diagonal derecha |
| `STAY` | `??? / ?X? / XXX` → `?X?` | Nada libre: se apila |

El centro es `X`, así que estas reglas valen para **cualquier** partícula. La
madera y el fuego se libran de ellas por prioridad, no por una excepción del
motor.

### Por qué ese orden

1. **El agua apagando el fuego antes que todo**: una llama que toca agua nunca
   llega a subir ni a propagarse, así que el agua es un muro de verdad y no un
   obstáculo que la llama rodea. El salto de la gota no necesita ese privilegio
   y va justo delante de la gravedad, donde solo le estorba caer.
2. **Los finales de vida después**: un `m` con llama al lado debe arder, no
   envejecer; y una madera agotada no debe moverse antes de convertirse.
3. **Madera antes que fuego**: el combustible decide antes de que la llama suba
   o se propague.
4. **El comportamiento del material antes que la gravedad**: el agua tiene que
   nivelar de lado *antes* de llegar a `STAY`, y el fuego subir antes de caer.
   Y dentro de la gravedad la nivelación va **antes** que los deslizamientos
   diagonales: un líquido no tiene ángulo de reposo, así que una gota que ha
   aterrizado se abre de lado y solo rueda por una pendiente cuando ya no le
   queda hueco lateral. Con las diagonales primero se apila en el mismo cono
   que la arena.
5. **La gravedad al final, como red**: cualquier material que no diga otra cosa
   se comporta como un grano de arena.
6. **La gota que sujeta un grano va con el agua**, antes de la gravedad, porque la
   marca es un estado de la gota y no un material: sin grano encima vuelve a ser
   agua antes de que le toque moverse. Y dentro de ese grupo la de subir va antes
   que la de envejecer, porque el `?N?` de envejecer encaja también con el último
   valor, que es justo el que tiene que subir.

---

## 9. La paleta y el dibujo

- 256 colores; el 0 es blanco (fondo).
- Cada banda se interpola entre dos o tres paradas de color (`Material.color`).
  La rampa se escribe en la lista de materiales, no en el código de dibujo.
- El último valor de un material con vida se interpola un 85 % hacia blanco: el
  fantasma.
- El dibujo escribe bloques de 8x8 píxeles en un `byte[]` en orden BGRA y lo
  manda a la imagen con una sola llamada (`PixelWriter.setPixels`). Sin
  asignaciones por celda: el bucle caliente no crea objetos.

---

## 10. Añadir cosas

### Un material nuevo

Una línea en `Material.LIST`:

```java
new Material('P', 40, true, 5, 3, "#ffffff", "#888888"),
```

Glifo, tamaño de la banda, si tiene vida, en cuántos valores puede nacer, **cada
cuántos pasos del mundo da un paso** y las paradas de color. La banda, la tabla
inversa, el botón del selector y la tecla salen solos. Si no se porta de forma
especial, cae por gravedad como la arena. El reloj es lo que lo hace rápido o
lento **frente a los demás materiales**: 1 es un paso en cada paso, 6 es uno de
cada seis y 12 es más lento que todo lo demás. Para acelerar o frenar el mundo
entero no se toca el reloj, se toca `ENGINE_MULTIPLIER`, y lo único que hay que
cuidar es que los turnos del material no caigan siempre en fotogramas del mismo
lado (ver "El reloj de cada material").

Un material que no se pinta, porque solo lo pone una regla, lleva un `false` más
antes de los colores: la marca `N` del agua que sujeta un grano se declara así y
así se queda fuera del selector y de las teclas.

```java
new Material('N', 6, false, 1, 6, false, "#8fcdf2", "#4591cd", "#1a4c80"),
```

### Una regla nueva

Un `Step` en `MaterialRules.Pattern` y su sitio en `STEPS`:

```java
private static final Step MI_REGLA = new Step(
        new String[] { "???", "?M?", "???" },
        new String[] { "???", "?W?", "???" });
```

Hay que respetar tres cosas:

1. `match` y `result` con la **misma forma** y tamaño **impar** (se valida al
   cargar, así que un error revienta al arrancar).
2. El carácter central del `match` tiene que encajar con la partícula que estás
   describiendo; el del `result` es la celda de la que sale.
3. **Todo material con vida necesita una regla que lo envejezca aunque no pueda
   moverse.** Si la única regla que lleva `W` pide hueco libre al lado, el
   material atrapado será inmortal.

### Un glifo nuevo de resultado

Un `case` en el `switch` de `applyPattern` y, si necesita dados, su cuenta en
`Step` (como `shared` y `copies`, para no contarla en cada partícula).

---

## 11. Compilar, ejecutar, comprobar

En este equipo (Windows, JDK 17 y JavaFX 21 por ruta absoluta, porque el `javac`
del PATH es otro):

```powershell
& "C:\Program Files\Java\jdk-17.0.20.1\bin\javac.exe" -Xlint:all `
  --module-path "C:\Program Files\Java\javafx-sdk-21.0.8\lib" `
  --add-modules javafx.controls,javafx.graphics -d bin src/principal/*.java src/sand/*.java

& "C:\Program Files\Java\jdk-17.0.20.1\bin\java.exe" `
  --module-path "C:\Program Files\Java\javafx-sdk-21.0.8\lib" `
  --add-modules javafx.controls,javafx.graphics -cp bin principal.MaterialSimulator
```

Para abrir el motor original, `-cp bin sand.Sand`.

Y para revisar la documentación:

```powershell
& "C:\Program Files\Java\jdk-17.0.20.1\bin\javadoc.exe" -Xdoclint:all,-missing `
  --module-path "C:\Program Files\Java\javafx-sdk-21.0.8\lib" `
  --add-modules javafx.controls,javafx.graphics -d docs src/principal/*.java src/sand/*.java
```

### Verificar sin abrir la ventana

Para tocar reglas, lo más rentable es un arnés temporal en el paquete
`principal`: una clase con su propia rejilla, su doble búfer y el mismo barrido
alternado, que llama a `stepParticle` en bucle y cuenta partículas por material.
Se compila junto con las fuentes y se borra al terminar.

Comprobaciones que han demostrado ser útiles (todas encontraron algo alguna vez):

| Comprobación | Qué vigila |
|---|---|
| Conservación de la arena | 100 granos entran, 100 granos salen |
| Centroide del montón | No se ladea (la alternancia del barrido) |
| La madera no cae | `WOOD_STAYS` |
| El fuego se apaga | Un fuego suelto no es inmortal |
| El fuego sube un tercio de pantalla | El ancho de su banda |
| El humo llega al techo | El ancho de la banda del humo |
| El agua apaga el fuego | Las ocho reglas de apagado |
| El agua no se pierde al apagar | 25 gotas entran, 25 gotas salen |
| Un fuego no cruza una mancha de agua | Que la llama no rodee la gota |
| Un bloque de madera se quema entero | Que el fuego no se limite a una columna |
| Quemar bajo la arena no deja residuo | Que todo material con vida envejezca atrapado |
| El agua se nivela | Las reglas laterales |
| Subir el multiplicador no cambia la forma | La arena con reloj 12 deja el mismo montón (centroide −0,14, alto 17, ancho 37) con el multiplicador en 4 que con 2 |
| La arena va a un doceavo | 73 filas de caída en 217 fotogramas, una cada 3 fotogramas (reloj 12 con 4 pasos por fotograma) |
| El agua cae doce veces más rápido | Las mismas 73 filas en 19 fotogramas, frente a los 217 de la arena |
| El reloj no pierde partículas | 330 granos entran, 330 salen; ídem con las gotas |
| El reloj no ladea las pilas | Bloque de 11x30 con reloj 12 y multiplicador 4: centroide a 0,14 celdas del centro, 17 de alto y 37 de ancho; con el cociente par (12 y multiplicador 2) se va 5,4 y se aplana (13 y 42) |
| El fuego y el humo suben lo mismo | 29 filas la llama (banda 30) y 57 el humo (hasta el techo) |
| El agua se aplana y la arena no | Cien gotas vertidas en un punto: lámina de 2 de alto en 100 columnas, desnivel 1; la misma arena deja un cono de 8 |
| El chorro sale recto | Con un grano cada dos turnos del material, el agua y la arena caen en una columna de 1 celda de ancho; con uno por turno, en 3 |
| El fotograma cabe de sobra | Pintado 0,95 ms y los cuatro pasos del mundo 0,45 ms, de los 16,6 ms que hay a 60 fps (100x75 celdas, 3750 granos) |

---

## 12. Trampas conocidas

Errores que ya se cometieron, con su síntoma, para no repetirlos:

| Síntoma | Causa | Regla |
|---|---|---|
| El material con vida no se gasta nunca | Su regla de envejecimiento pide algo que no tiene (hueco encima) | Toda regla que deba envejecer lleva `W`/`V`/`r`/`l` en el centro, y hay al menos una que encaja siempre |
| El fuego es infinito en cada generación | Poner una letra de material en el `result` (`?F?`) | Una letra en el `result` **crea**; si solo se quiere mantener, es `X` |
| La madera renace con la vida a tope | `M → M` | `M → X`: el material se conserva, la vida no se resetea |
| La madera se quema en 5 fotogramas | Usar `r` (mitad de la vida restante) con bandas grandes | `W` para consumir despacio, un valor por generación |
| Se pierden partículas al deslizar | Escribir el destino sin comprobar si está libre | `isFree` antes de escribir; si falla, la partícula vuelve a su sitio |
| Las partículas se duplican | Copiar a los vecinos con `X` en el `result` | Los `?` no se copian: cada partícula se mueve en su propia regla |
| Las pilas crecen torcidas | Barrido siempre en el mismo sentido | Alternar el sentido cada fotograma (`frameCounter & 1`) |
| Un patrón no coincide nunca | Olvidar que el centro del dibujo es la partícula | Ancla calculada: el carácter central tiene que ser la partícula |
| La barra inferior aparece recortada | Sumar a mano los altos de la escena | Dejar que la escena pregunte el tamaño al `root` (`new Scene(root)`) |
| El fuego se descontrola y acaba llenando el tablero | Dar a la copia de `o` una edad constante (`last - size / copies + 1`) | El hijo calcula su valor **desde el padre**: una constante le estrena el reloj y la estirpe deja de caducar nunca |
| El agua no apaga el fuego, o lo apaga perdiendo gotas | Intentar matar la llama desde el agua, escribiendo en su celda | La llama se apaga desde **su** regla (`?A? / ?F?` → `?_?`): el fuego se escribe a sí mismo al final de su paso y borraría la gota |
| La gota de agua desaparece al saltar al fuego | Salto hacia abajo o de lado | La gota solo salta hacia arriba: la llama de encima ya se barrió y su celda está libre |
| Una partícula desaparece cada vez que le toca descansar | Omitir el paso de la que no tiene el turno | La celda sin turno se copia tal cual al búfer siguiente (`nextCells[y][x] = value`) |
| Las pilas se inclinan desde que hay relojes | El sentido del barrido se alterna por paso, o el cociente entre el reloj y el multiplicador es par: todos los turnos del material caen en la misma paridad | Alternar el sentido por **fotograma** y dejar impar `reloj / multiplicador` cuando el reloj es mayor que el multiplicador (12 / 4 = 3) |
| Un material va a la mitad de velocidad de la que se le supone | Contar su reloj en fotogramas cuando el mundo da varios pasos por fotograma | El reloj de un material se cuenta en **pasos** (`stepCounter`); el fotograma es solo del golpe y del pintado |
| Al subir `ENGINE_MULTIPLIER` el chorro se abre | El pincel medido en fotogramas y el material en pasos: al subir el multiplicador el grano nace pegado al anterior | Medir el pincel en los mismos pasos que el material (`stepCounter % (stepPeriod * 2)`), así el hueco se mantiene con cualquier multiplicador |
| El chorro se abre en cono de tres columnas | El pincel suelta un grano por turno del material: nace en la celda que el anterior acaba de dejar y la generación anterior todavía lo ocupa | Gotear cada **dos** turnos del material (`stepCounter % (stepPeriod * 2)`), que deja un turno de hueco |

El número de `o` no cambia el ritmo: cada `o` tira un dado con tantas caras como
`o` hay, así que el número esperado de hijos por generación es siempre **uno**.
Lo que cambia con el número de `o` es *dónde* pueden aparecer, no cuántos. Ese
uno esperado es justo el punto crítico, y por eso una pluma de fuego se sostiene
muchas generaciones pero siempre acaba apagándose: cualquier cosa que le regale
vida (o que le reinicie el reloj a las copias) la empuja al otro lado, y
quitársela —que es lo que hace el reparto de `o`— la apaga mucho antes.

---

## 13. Pendientes e ideas

- **El calor del agua**: hoy la gota apaga la llama y sigue siendo agua; no se
  calienta ni se convierte en vapor. El humo que sube es el del fuego, no el del
  agua que lo apagó.
- **Densidad**: hoy la arena se queda encima del agua porque las dos son
  "partícula". Para que se hunda hace falta que el agua sea hueco para la arena,
  es decir, una regla de arena que intercambie con agua.
- **Propagación lateral de la llama**: hoy un fuego pintado al aire se escapa
  hacia arriba y no llega a encender una pared que tenga al lado. Se arreglaría
  con una regla de fuego que no suba cuando tiene madera al lado.
- **Arnés permanente**: el arnés se ha usado y borrado tres veces. Merece la pena
  dejarlo en el repositorio como prueba de humo.
- **El ciclo `V`**: implementado y sin usar. Es la pieza que falta para
  materiales que se transforman en bucle (hielo ↔ agua, por ejemplo).
