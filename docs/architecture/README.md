# Arquitectura - RutaTur

Esta sección contiene la documentación arquitectónica del proyecto RutaTur siguiendo el modelo C4 de Simon Brown.

## Modelo C4

El modelo C4 proporciona una forma jerárquica para visualizar la arquitectura de software:

- **C1 - Contexto**: Sistema en su entorno, actores y sistemas externos
- **C2 - Contenedores**: Aplicaciones, microservicios, bases de datos y componentes
- **C3 - Componentes** (opcional): Descomposición interna de un contenedor
- **C4 - Código** (opcional): Detalles de implementación

Para este TPI se entregan los niveles C1 y C2 según lo requerido.

## Diagramas

### [C1 - Diagrama de Contexto](./c4-c1-context.puml)

Muestra el sistema RutaTur Backend en su contexto, los actores que interactúan con él y los sistemas externos con los que se integra.

![Diagrama C1 - Contexto](./c4-c1-context.png)

### [C2 - Diagrama de Contenedores](./c4-c2-containers.puml)

Muestra los contenedores que conforman el sistema: API Gateway, microservicios, bases de datos, Keycloak y sus interrelaciones.

![Diagrama C2 - Contenedores](./c4-c2-containers.png)

## Diagrama de Secuencia - Generación de Itinerario

Para demostrar la **colaboración real** entre microservicios:

![Diagrama de Secuencia](./sequence-generate-itinerary.png)

[Ver código PlantUML](./sequence-generate-itinerary.puml)

## Leyenda

- 🔵 **Restricción del TPI**: Componente/integración cuya existencia está establecida por el enunciado
- 🟢 **Decisión del equipo**: Responsabilidad, contenido o diseño surgido del análisis del equipo

## Archivos

| Archivo | Descripción |
|---------|-------------|
| `c4-c1-context.puml` | PlantUML del diagrama de contexto |
| `c4-c2-containers.puml` | PlantUML del diagrama de contenedores |
| `sequence-generate-itinerary.puml` | PlantUML del diagrama de secuencia |
| `c4-c1-context.png` | Imagen del diagrama C1 |
| `c4-c2-containers.png` | Imagen del diagrama C2 |
| `sequence-generate-itinerary.png` | Imagen del diagrama de secuencia |

## Herramientas para Visualizar

- [PlantUML](https://plantuml.com/) - Para renderizar archivos .puml
- [PlantUML Online Server](http://www.plantuml.com/plantuml/uml/)
- [C4-PlantUML](https://github.com/plantuml-stdlib/C4-PlantUML) - Librería para diagramas C4
