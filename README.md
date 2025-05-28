# APCSA Task Website

## Overview
A web application for managing APCSA (AP Computer Science A) tasks. This platform allows users to work on and submit programming assignments.

## Technologies

### Backend
- Java 17
- Spring Boot 3.4.4
- Spring Security
- Spring Data JPA
- PostgreSQL (production environment)
- H2 Database (development environment)

### Frontend
- Thymeleaf
- TailwindCSS 3.3.3
- PostCSS 8.4.27
- Autoprefixer 10.4.14

## Features
- User management with authentication and authorization
- Task management
- Submissions and evaluations
- User-friendly interface with TailwindCSS

## Installation and Running

### Prerequisites
- Java 17 or higher
- Maven
- Node.js and NPM
- PostgreSQL (for production environment)

### Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/username/apcsa-Task-website.git
   cd apcsa-Task-website
   ```

2. Install Maven dependencies:
   ```bash
   ./mvnw install
   ```

3. Install NPM packages:
   ```bash
   npm install
   ```

4. Start the application:
   ```bash
   ./mvnw spring-boot:run
   ```

5. Access the application at:
   ```
   http://localhost:8080
   ```

## Development
The application uses Maven for Java dependencies and NPM for frontend components. TailwindCSS is used for styling.

## Docker
The project includes Dockerfile.coderunner for containerizing the application.

## License
This project is licensed under the MIT License. See [LICENSE](LICENSE.md) for details.
