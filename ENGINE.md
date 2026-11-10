# El motor de arena: materiales, reglas y sistema

Documento de referencia del simulador. Explica **cómo está montado** y, sobre
todo, **por qué** cada pieza es como es: la mayoría de las decisiones de aquí
salieron de un fallo concreto, así que cada una viene con su motivo.

Si dentro de un mes algo no cuadra, el orden de lectura recomendado es: las
secciones 4 (la célula), 5 (la vida) y 7 (cómo se aplica una regla). Con esas
tres se puede leer cualquier regla de la tabla y saber qué hace.

---

## 1. La idea en una frase

Cada célula guarda **un número** (un índice de paleta) que dice a la vez *de qué
material es* y *cuánta vida le queda*. Cada generación, cada partícula busca en
una tabla la **primera regla que encaja** con su vecindad y el resultado de esa
regla dice qué se escribe en la generación siguiente.

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
| [Sand.java](src/principal/Sand.java) | Ventana, barra de materiales, teclado, bucle de 60 FPS |
| [Renderer.java](src/principal/Renderer.java) | Doble búfer, paleta, pincel del ratón, golpes, pintado a píxeles |
| [Transition.java](src/principal/Transition.java) | Leer vecindades y aplicar las reglas: el motor, literalmente |
| [Material.java](src/principal/Material.java) | La tabla de materiales: glifo, banda de valores, vida y colores |

Toda la configuración vive en dos sitios: la lista `Material.LIST` (qué
materiales existen) y la tabla `Pattern.STEPS` (qué hacen). El resto del código
es maquinaria que no hay que tocar para añadir cosas.

---

## 3. Un fotograma, paso a paso

`Renderer.tick()` se ejecuta 60 veces por segundo:

1. **Pincel**: cada dos fotogramas (`DROP_EVERY_FRAMES = 2`) se pone una
   partícula del material seleccionado en la celda del ratón, o en un cuadrado de
   2 celdas de radio si se mantiene SPACE. Solo en celdas vacías.
2. **Golpe** (si hay uno activo): `pushParticles()` empuja lo que haya hacia un
   lado y el resultado pasa a ser la generación actual.
3. **Barrido**: se recorre la rejilla fila por fila y, en cada celda no vacía, se
   llama a `Transition.stepParticle()`, que aplica la primera regla que encaja y
   escribe en el búfer siguiente.
4. **Intercambio**: `advanceGeneration()` cambia `cells` por `nextCells` y vacía
   el que queda libre. Nunca se copian datos: se intercambian las referencias.
5. **Pintado**: la rejilla se expande a bloques de 8x8 píxeles en un `byte[]`
   BGRA y se manda a la imagen en una sola llamada.

### Dos detalles del barrido que importan

- **Doble búfer**: `cells` es lo que se lee, `nextCells` lo que se escribe. Nadie
  mira `nextCells` salvo para comprobar si una celda sigue libre (`isFree`).
  Gracias a eso una partícula no puede "verse" a sí misma dos veces.
- **El sentido del barrido se alterna** cada generación (`frameCounter & 1`).
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
| `1` a `167` | un material, con su vida |
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
cualquiera de los 80 valores del fuego.

### Los materiales

| Glifo | Material | Valores | Vida | Nace en | Rampa |
|---|---|---|---|---|---|
| `S` | arena | 1-25 | no | 1-25 | amarillo pálido → ocre dorado |
| `A` | agua | 26-45 | no | 26-45 | azul claro → azul profundo |
| `M` | madera | 46-75 | sí (30 pasos) | 46-54 | madera clara → madera oscura |
| `F` | fuego | 76-155 | sí (80 pasos) | 76-81 | amarillo pálido → naranja → rojo |
| `H` | humo | 156-167 | sí (12 pasos) | 156-159 | gris oscuro → gris claro |

Las bandas se asignan solas en el orden de la lista, desde 1. Añadir un material
es añadir una línea; los valores se recalculan.

El ancho de cada banda no es decorativo: **es la vida**. La del fuego son 80
pasos porque subir cuesta un paso por fila y el lienzo por defecto mide 75
filas: con menos vida, una llama pintada en el suelo se apaga a media altura,
que es exactamente lo que pasaba con 30. La madera se queda con 30 porque su
vida es lo que tarda en arder, no lo que sube.

---

## 5. La vida es la posición en la banda

Para un material con vida, la posición dentro de su banda **es** su edad. Cuatro
glifos la mueven, y solo cuatro:

| Glifo | Efecto | Para qué |
|---|---|---|
| `V` | +1, y al llegar al final vuelve al principio | Ciclos sin fin (no se usa todavía) |
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
  blanco, en `Renderer.Palette`). Es un **fantasma**: el material sigue ahí,
  invisible, esperando la regla que lo transforme. Por eso la madera parece
  desaparecer justo antes de arder.
- Ningún material nace con un valor fijo: `spawn()` sortea entre los primeros
  valores de su banda (`spread`), para que un montón no sea una plancha de un
  solo color. Sin vida eso solo cambia el tono. Con vida el valor **es** la edad,
  así que el sorteo es también vida gastada al nacer, y por eso el margen se
  queda corto y distinto en cada material: la madera sortea entre 9 valores de 30
  (y por eso arde un poco antes), y el fuego entre 6 de 80, que es justo lo que
  se puede gastar sin dejar de llegar al techo.

### La cadena completa de una pared de madera

```
madera  M (46..75)  --W-->  m (75, fantasma)
                            --WOOD_END-->  F (76, fuego nuevo)
                                           --W-->  f (155, fantasma)
                                                  --FIRE_END-->  H (humo)
                                                                 --W-->  h (167, fantasma)
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
| `S` `A` `M` `F` `H` | cualquier valor de ese material |
| `s` `a` `m` `f` `h` | **solo el último valor** de ese material: existe pero ya no tiene vida |

### En el `result` (la escritura)

| Glifo | Qué escribe |
|---|---|
| `?` | nada (deja la celda como esté) |
| `_` | vacío |
| `X` | la partícula, con su valor exacto |
| `S` `A` `M` `F` `H` | una partícula **nueva** de ese material, con `spawn()` |
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

### Finales de vida (siempre primero)

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
| `WATER_FLOWS_BOTH` | `_A_ / XXX` → `x_x` | Con suelo firme y los dos lados libres, se desliza a uno de ellos |
| `WATER_FLOWS_LEFT` | `_AX / XXX` → `X_?` | Solo el lado izquierdo libre |
| `WATER_FLOWS_RIGHT` | `XA_ / XXX` → `?_X` | Solo el lado derecho libre |

Esto es lo que hace que el agua **nivele** en vez de apilarse en pirámide como la
arena. Usa `x` (compartido) y no dos `X`: el agua se reparte, no se duplica. Y
como los lados de la condición son `_`, el destino está garantizado libre.

Van **después** de los deslizamientos diagonales (una gota que puede caer en
diagonal, cae) y **antes** de `STAY` (si fueran después, el agua se apilaría y
nunca llegaría a ellas).

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

1. **Los finales de vida primero**: un `m` con llama al lado debe arder, no
   envejecer; y una madera agotada no debe moverse antes de convertirse.
2. **Madera antes que fuego**: el combustible decide antes de que la llama suba
   o se propague.
3. **El comportamiento del material antes que la gravedad**: el agua tiene que
   deslizarse de lado *antes* de llegar a `STAY`, y el fuego subir antes de
   caer.
4. **La gravedad al final, como red**: cualquier material que no diga otra cosa
   se comporta como un grano de arena.

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
new Material('P', 40, true, 5, "#ffffff", "#888888"),
```

Glifo, tamaño de la banda, si tiene vida, en cuántos valores puede nacer y las
paradas de color. La banda, la tabla inversa, el botón del selector y la tecla
salen solos. Si no se porta de forma especial, cae por gravedad como la arena.

### Una regla nueva

Un `Step` en `Transition.Pattern` y su sitio en `STEPS`:

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
  --add-modules javafx.controls,javafx.graphics -d bin src/principal/*.java

& "C:\Program Files\Java\jdk-17.0.20.1\bin\java.exe" `
  --module-path "C:\Program Files\Java\javafx-sdk-21.0.8\lib" `
  --add-modules javafx.controls,javafx.graphics -cp bin principal.Sand
```

Y para revisar la documentación:

```powershell
& "C:\Program Files\Java\jdk-17.0.20.1\bin\javadoc.exe" -Xdoclint:all,-missing `
  --module-path "C:\Program Files\Java\javafx-sdk-21.0.8\lib" `
  --add-modules javafx.controls,javafx.graphics -d docs src/principal/*.java
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
| Un bloque de madera se quema entero | Que el fuego no se limite a una columna |
| Quemar bajo la arena no deja residuo | Que todo material con vida envejezca atrapado |
| El agua se nivela | Las reglas laterales |

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
| Las pilas crecen torcidas | Barrido siempre en el mismo sentido | Alternar el sentido por generación |
| Un patrón no coincide nunca | Olvidar que el centro del dibujo es la partícula | Ancla calculada: el carácter central tiene que ser la partícula |
| La barra inferior aparece recortada | Sumar a mano los altos de la escena | Dejar que la escena pregunte el tamaño al `root` (`new Scene(root)`) |
| El fuego se descontrola y acaba llenando el tablero | Dar a la copia de `o` una edad constante (`last - size / copies + 1`) | El hijo calcula su valor **desde el padre**: una constante le estrena el reloj y la estirpe deja de caducar nunca |

El número de `o` no cambia el ritmo: cada `o` tira un dado con tantas caras como
`o` hay, así que el número esperado de hijos por generación es siempre **uno**.
Lo que cambia con el número de `o` es *dónde* pueden aparecer, no cuántos. Ese
uno esperado es justo el punto crítico, y por eso una pluma de fuego se sostiene
muchas generaciones pero siempre acaba apagándose: cualquier cosa que le regale
vida (o que le reinicie el reloj a las copias) la empuja al otro lado, y
quitársela —que es lo que hace el reparto de `o`— la apaga mucho antes.

---

## 13. Pendientes e ideas

- **El agua apaga el fuego**: hablado y aplazado. Sería una regla escrita desde
  el agua (mirando al fuego) que lo convierta en humo.
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
