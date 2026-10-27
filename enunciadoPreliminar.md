Trabajo Práctico Integrador
Backend de Aplicaciones — 2026
Versión preliminar — Lineamientos generales
Para empezar a pensar
Introdu
cción
El Trabajo Práctico Integrador tiene como objetivo diseñar, implementar, integrar, asegurar y desplegar
una solu
ción backend basada en microservicios, aplicando de manera integrada los conceptos
desarrollados durante la asignatura.
A diferencia de actividades anteriores, el TPI no propone un dominio completamente especificado ni
determina de antemano la solu
ción funcional o arq
uitectónica.
Cada eq
uipo deberá seleccionar un dominio de problema,
comprenderlo, establecer su alcance y diseñar
una solu
ción q
ue permita demostrar los conocimientos adq
uiridos durante el cursado.
Como parte de este docu
mento se presenta un conjunto de dominios de problema sugeridos. Estos
dominios funcionan únicamente como disparadores y podrán ser extendidos, reinterpretados o
reemplazados por una prop
uesta propia del eq
uipo.
El problema finalmente seleccionado deberá permitir construir una solu
ción con suficiente riq
ueza
funcional como para justificar las decisiones de diseño, integración, seguridad y despliegue adoptadas.
O
bjetivo del trabajo
Esperamos q
ue cada eq
uipo construya una solu
ción backend en la q
ue sea posible identificar y justificar:
responsabilidades funcionales claramente diferenciadas;
reglas de negocio relevantes;
colaboración entre componentes;
integración con información proveniente de sistemas externos;
autenticación y autorización;
reutilización de componentes;
exposición controlada de servicios;
persistencia de información;
docu
mentación de las APIs;
contenerización y despliegue integral de la solu
ción.
El objetivo no es solamente lograr q
ue un conjunto de servicios funcione.
Esperamos q
ue el eq
uipo p
ueda explicar por q
u
é la solu
ción fue diseñada de esa manera, q
u
é
alternativas fueron consideradas y q
u
é consecuencias tienen las decisiones tomadas.
Definición del problema
Cada eq
uipo deberá comenzar por comprender y definir el problema q
ue pretende resolver.
enunciado.md 2026-10-05
1 / 9
Podrá utilizar uno de los dominios sugeridos como p
unto de partida, extenderlo, reinterpretarlo o
proponer un dominio diferente.
Como parte de este proceso, el eq
uipo debería lograr identificar,
como mínimo:
contexto general del problema;
actores involu
crados;
principales procesos;
información relevante;
reglas de negocio identificadas;
responsabilidades q
ue deberá asu
mir el backend;
información q
ue deberá obtenerse desde sistemas externos.
Esta definición no constituye un entregable docu
mental en sí misma.
El objetivo es q
ue el eq
uipo realice el análisis necesario para construir una visión compartida del
problema, acordar q
u
é pretende resolver y disponer de una base común sobre la cual diseñar y
desarrollar la solu
ción.
No se espera q
ue esta definición permanezca inalterable durante todo el trabajo.
El eq
uipo podrá iterar sobre el problema y sobre el diseño de la solu
ción a medida q
ue descubra
nuevas necesidades, restricciones o alternativas.
Estas iteraciones forman parte del trabajo esperado. Las decisiones relevantes y los cambios
significativos deberán poder ser explicados durante la presentación y defensa del TPI.
Diseño de la solu
ción
Antes de comenzar la implementación, el eq
uipo deberá elaborar:
un Diagrama de Contexto — C1 utilizando C4 Model;
un Diagrama de Contenedores — C2 utilizando C4 Model;
siguiendo la prop
uesta de Simon Brown.
Los diagramas deberán permitir comprender progresivamente la solu
ción y deberán identificar, según el
nivel correspondiente:
actores;
sistema a desarrollar;
sistemas externos;
API Gateway;
microservicios funcionales;
Identity Provider;
DBMS;
integraciones externas;
componentes de infraestru
ctura relevantes;
relaciones y com
unicaciones entre los diferentes componentes.
El diseño inicial no constituye un contrato inalterable.
enunciado.md 2026-10-05
2 / 9
Esperamos q
ue evolu
cione a medida q
ue el eq
uipo profundice el problema, implemente la solu
ción y
descubra nuevas restricciones o alternativas.
La versión presentada en la entrega deberá representar la arq
uitectura efectivamente implementada.
Diagramas de referencia
Los siguientes diagramas tienen como único objetivo representar las restricciones establecidas por el
TPI y los espacios de decisión q
ue corresponden a cada eq
uipo.
No constituyen una arq
uitectura de referencia q
ue deba reprodu
cirse literalmente.
En ambos diagramas:
🟦 Restricción del TPI: componente, integración o capacidad cuya existencia está establecida por
el enunciado.
🟩 Decisión del eq
uipo: responsabilidad,
contenido o diseño q
ue deberá surgir del análisis
realizado por el gru
po.
C1 — Diagrama de Contexto genérico
utiliza
identidad / autenticación
consume información
👥 Usuarios /
Actores
Roles a definir
Sistema
Backend TPI
Dominio,
procesos y
reglas
a definir por el
equipo
Keycloak
Identity
Provider
OIDC · JWT ·
Roles
API pública
externa
Información
necesaria
para el negocio
En este nivel todavía no interesa mostrar cómo está construido internamente el backend.
El C1 deberá permitir comprender q
u
é problema resuelve el sistema, q
uiénes interactúan con él y q
u
é
otros sistemas externos participan de su
contexto.
C2 — Diagrama de Contenedores genérico
enunciado.md 2026-10-05
3 / 9
Sistema Backend TPI
≥ 2 Microservicios funcionales
colaboración funcional
dependencia
dependencia
autenticación /
autorización
consume
consume
👥 Usuarios
Roles a definir
Keycloak
IdP
OIDC · JWT ·
Roles
API pública
externa
Información
necesaria
para el negocio
API Gateway
Routing +
Security Filter
Microservicio A
Responsabilidad
funcional
a definir Microservicio B
Responsabilidad
funcional
a definir
Persistencia
DBMS / Base de
datos
Componente
común
JAR · Maven
El diagrama representa q
ue la existencia de al menos dos microservicios funcionales constituye una
restricción del TPI, pero sus responsabilidades, límites e interacciones deberán ser descubiertos y
diseñados por cada eq
uipo.
De la misma manera, se representan únicamente las relaciones necesarias para comprender las
restricciones generales. Cada solu
ción podrá incorporar otros componentes, relaciones e integraciones
cuando el diseño adoptado lo req
uiera.
Arq
uitectura de la solu
ción
La solu
ción deberá implementarse siguiendo una arq
uitectura basada en microservicios, incorporando
un API Gateway como p
unto de entrada al backend.
Microservicios funcionales
La solu
ción deberá contener al menos dos microservicios funcionales desarrollados en Java con
Spring Boot.
La división entre microservicios deberá responder a responsabilidades funcionales coherentes dentro
del dominio elegido.
No será suficiente dividir arbitrariamente una aplicación en dos proyectos ni implementar dos conjuntos
independientes de operaciones CR
UD únicamente para satisfacer este req
uisito.
Cada microservicio deberá poseer una responsabilidad identificable dentro del dominio y mantener la
información necesaria para cu
mplirla.
Cada eq
uipo deberá descubrir y justificar los límites funcionales apropiados para su
propio dominio.
Colaboración entre microservicios
La solu
ción deberá contener procesos q
ue req
uieran colaboración real entre microservicios.
Al menos un proceso significativo de negocio deberá involu
crar la participación de más de un
microservicio funcional.
La interacción deberá responder a una necesidad real del dominio y no haber sido incorporada
artificialmente con el único propósito de demostrar com
unicación entre servicios.
Por ejemplo, si un microservicio administra clientes y otro administra pedidos, el servicio responsable de
los pedidos podría necesitar obtener información del cliente para ejecutar determinadas operaciones de
negocio.
enunciado.md 2026-10-05
4 / 9
El ejemplo anterior es únicamente ilustrativo y no establece una división esperada para los trabajos.
Se evaluará especialmente:
q
u
é servicio inicia la interacción;
q
u
é información necesita de otros componentes;
q
u
é responsabilidad conserva cada servicio;
cómo se resuelven situaciones en las q
ue otro componente no responde;
q
u
é información pertenece a cada responsabilidad funcional;
q
u
é decisiones permiten mantener un nivel razonable de independencia entre los servicios.
No se prescribe una única estrategia para resolver estas interacciones.
API Gateway
La solu
ción deberá disponer de un API Gateway como p
unto de entrada a los servicios backend.
El Gateway deberá encargarse del enrutamiento hacia los diferentes microservicios y participar de la
estrategia de seguridad definida por el eq
uipo.
Los consu
midores externos no deberían necesitar conocer la topología interna de la solu
ción para utilizar
sus funcionalidades.
La implementación deberá incorporar además un Security Filter en el Gateway, acorde con el esq
uema
de autenticación y autorización adoptado.
La existencia del Gateway es una restricción arq
uitectónica del TPI. Su
configuración, las rutas exp
uestas
y su
participación concreta dentro de la estrategia de seguridad forman parte de las decisiones q
ue
deberá tomar y justificar cada eq
uipo.
Integración con una API pública externa
La solu
ción deberá consu
mir al menos una API pública externa accesible a través de Internet.
La integración deberá proporcionar información q
ue resulte genuinamente necesaria para resolver
alguna funcionalidad o regla de negocio de la solu
ción.
No se considerará una integración significativa aq
uella q
ue únicamente incorpore información decorativa,
datos accesorios o valores q
ue razonablemente podrían almacenarse como configuración propia del
sistema.
Por ejemplo,
utilizar una API exclusivamente para consultar el valor de cambio entre monedas no
satisface por sí mismo este req
uerimiento.
Esperamos q
ue el eq
uipo p
ueda explicar:
por q
u
é necesita obtener esa información externamente;
q
u
é proceso de negocio depende de ella;
cómo se realiza la integración;
q
u
é información se utiliza;
q
u
é su
cede cuando el servicio externo no se encuentra disponible o devuelve una resp
uesta
inesperada.
enunciado.md 2026-10-05
5 / 9
Se exige al menos una integración externa, pero no se establece una cantidad máxima.
La cantidad de integraciones incorporadas no será considerada por sí misma una medida de complejidad.
Se evaluará fundamentalmente q
u
é aportan al problema y cómo participan de la solu
ción.
Identidad, autenticación y autorización
La solu
ción deberá utilizar Keycloak como Identity Provider (IdP).
La arq
uitectura de seguridad deberá incorporar:
OpenID Connect (OIDC);
tokens JWT;
Spring Security;
roles administrados mediante Keycloak;
autenticación de los usuarios;
autorización de las operaciones de acuerdo con los roles definidos;
integración de la seguridad con el API Gateway.
El eq
uipo deberá identificar los diferentes tipos de usuarios q
ue interactúan con su solu
ción y determinar
q
u
é operaciones p
uede realizar cada uno.
No se prescribe una única estrategia para distribuir las responsabilidades de seguridad entre Gateway y
microservicios.
Cada eq
uipo deberá diseñar y justificar cómo establece su trusted zone, q
u
é componentes p
ueden
com
unicarse entre sí y q
u
é mecanismos utiliza para asegurar q
ue las operaciones protegidas sólo
p
uedan ser ejecutadas por usuarios debidamente autenticados y autorizados.
Componente común reutilizable
La solu
ción deberá incluir al menos un componente Java reutilizable empaq
uetado como JAR
.
Este componente deberá construirse como un artefacto Maven independiente y deberá ser utilizado
como dependencia por, al menos, los dos microservicios funcionales principales.
Como mecanismo mínimo, el artefacto deberá ser desplegado/instalado en el repositorio Maven local
(.m2) y posteriormente referenciado desde los microservicios mediante las dependencias
correspondientes de sus archivos pom.xml.
El eq
uipo podrá optar por utilizar un repositorio Maven compartido u otro mecanismo de distribu
ción
de artefactos si desea explorar una solu
ción más completa. Esto no constituye un req
uisito adicional: el
repositorio Maven local representa el mecanismo mínimo esperado.
No se espera q
ue el código fuente del componente sea simplemente copiado entre proyectos. Los
microservicios deberán consu
mir el artefacto generado como una dependencia Maven.
Cada eq
uipo decidirá q
u
é responsabilidades resulta razonable incorporar en este componente común.
El objetivo no consiste simplemente en compartir código, sino en identificar elementos
verdaderamente reutilizables y diseñar una dependencia coherente.
enunciado.md 2026-10-05
6 / 9
Durante la defensa deberá poder justificarse tanto aq
uello q
ue se decidió compartir como aq
uello q
ue
deliberadamente permaneció dentro de cada microservicio.
Persistencia
La solu
ción deberá utilizar al menos un DBMS relacional o una base de datos embebida para la
persistencia de la información.
Como solu
ción base, los diferentes componentes podrán utilizar una instancia común del DBMS.
Esto no implica q
ue todos los microservicios deban compartir necesariamente el mismo modelo de datos
ni acceder indistintamente a todas las estru
cturas existentes.
Esta alternativa no es limitante. Será completamente válido utilizar:
una base de datos por microservicio;
esq
uemas diferentes dentro de un mismo DBMS;
diferentes estrategias de separación de la información;
otras alternativas q
ue resulten coherentes con la arq
uitectura prop
uesta.
El eq
uipo deberá poder explicar q
u
é estrategia adoptó y por q
u
é resulta adecuada para su solu
ción.
APIs REST y docu
mentación
Los microservicios deberán exponer sus funcionalidades mediante APIs REST.
Las APIs deberán:
utilizar apropiadamente los métodos HTTP;
utilizar códigos de resp
uesta HTTP coherentes;
intercambiar representaciones JSON cuando corresponda;
validar los datos recibidos;
contemplar y com
unicar adecuadamente situaciones de error;
estar docu
mentadas mediante OpenAPI / Swagger.
La docu
mentación deberá representar la API efectivamente implementada.
Contenerización y despliegue
Los componentes necesarios para ejecutar la solu
ción deberán estar contenerizados.
El eq
uipo deberá proporcionar una configuración mediante Docker Compose q
ue permita desplegar la
solu
ción integral de manera reprodu
cible.
El despliegue deberá contemplar, según corresponda:
API Gateway;
microservicios funcionales;
Keycloak;
DBMS;
componentes adicionales desarrollados o incorporados por el eq
uipo;
configuración necesaria para la com
unicación entre ellos.
enunciado.md 2026-10-05
7 / 9
Las APIs públicas utilizadas por la solu
ción podrán naturalmente permanecer fuera de esta
infraestru
ctura.
El objetivo es q
ue la solu
ción p
ueda ser desplegada como un sistema completo, y no como un conjunto
de aplicaciones q
ue req
uieran configuraciones manuales independientes para poder funcionar.
La arq
uitectura representada en el C2 entregado por el eq
uipo deberá corresponderse con los
componentes q
ue efectivamente formen parte de este despliegue.
Nivel de desafío
Los req
uisitos establecidos en este docu
mento representan el p
unto de partida común para todos los
eq
uipos.
No todos los dominios presentan la misma dificultad y no todos los eq
uipos necesariamente elegirán
asu
mir el mismo nivel de desafío.
Se evaluará tanto el cu
mplimiento de los req
uisitos mínimos como el nivel de desafío asu
mido por el
eq
uipo,
considerando especialmente:
riq
ueza del dominio seleccionado;
complejidad y relevancia de las reglas de negocio;
calidad de la división funcional entre microservicios;
complejidad de los procesos q
ue atraviesan diferentes componentes;
relevancia de las integraciones externas;
decisiones de seguridad;
decisiones arq
uitectónicas;
tratamiento de errores y situaciones excepcionales;
robustez general de la solu
ción.
Una mayor cantidad de microservicios, tecnologías o integraciones no implica automáticamente una
solu
ción de mayor calidad o complejidad.
Las extensiones deberán aportar valor al problema planteado y deberán poder ser justificadas por el
eq
uipo.
Un eq
uipo podrá decidir profundizar determinados aspectos de la solu
ción y asu
mir desafíos adicionales
siempre q
ue mantenga una arq
uitectura coherente y una solu
ción funcional.
Proceso de diseño y constru
cción
El TPI deberá ser entendido como un proceso iterativo de diseño e implementación.
No esperamos q
ue la primera idea de arq
uitectura, la primera división de responsabilidades o la primera
solu
ción prop
uesta sean necesariamente las definitivas.
Esperamos q
ue los eq
uipos:
1.
comprendan el problema;
2.
construyan una visión compartida;
3. propongan una solu
ción inicial;
enunciado.md 2026-10-05
8 / 9
4. identifiq
uen responsabilidades;
5. diseñen la arq
uitectura;
6. implementen una primera solu
ción;
7. descubran problemas y limitaciones;
8. revisen sus decisiones;
9. evolu
cionen la implementación;
10. validen integralmente el resultado.
Durante este proceso podrán utilizar las herramientas q
ue consideren convenientes para investigar,
diseñar, implementar, probar y docu
mentar la solu
ción.
La utilización de herramientas de asistencia, incluyendo herramientas basadas en Inteligencia Artificial,
no reemplaza la responsabilidad del eq
uipo sobre las decisiones adoptadas ni sobre el código
presentado.
El eq
uipo deberá ser capaz de comprender, modificar y explicar la solu
ción independientemente de las
herramientas utilizadas durante su
constru
cción.
Durante la defensa,
cualq
uiera de los integrantes deberá poder explicar las decisiones relevantes y el
funcionamiento de la solu
ción construida.
Consideraciones finales
Los dominios prop
uestos para el TPI son deliberadamente abiertos.
No esperamos q
ue los eq
uipos pregunten q
u
é microservicios deben construir para cada dominio.
Determinar las responsabilidades, establecer los límites entre componentes, identificar las integraciones
necesarias y diseñar los procesos de negocio forma parte del Trabajo Práctico Integrador.
De la misma manera, no existe una arq
uitectura única considerada correcta de antemano.
Esperamos solu
ciones diferentes entre eq
uipos y esperamos q
ue esas diferencias sean consecuencia de
las decisiones tomadas durante el análisis y la constru
cción.
El objetivo final del TPI no será demostrar únicamente q
ue una aplicación funciona, sino demostrar q
ue el
eq
uipo es capaz de comprender un problema y diseñar,
construir, integrar, asegurar, desplegar y
defender una solu
ción backend completa.
enunciado.md 2026-10-05
9 / 9