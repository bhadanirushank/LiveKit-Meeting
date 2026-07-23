const fs = require('fs');
const yaml = require('js-yaml');

const openapiPath = 'docs/openapi.yaml';
const doc = yaml.load(fs.readFileSync(openapiPath, 'utf8'));

const errorResponseRef = {
  description: "Bad Request",
  content: {
    "application/json": {
      schema: {
        $ref: "#/components/schemas/ErrorResponse"
      }
    }
  }
};
const errorResponse404Ref = {
  description: "Not Found",
  content: {
    "application/json": {
      schema: {
        $ref: "#/components/schemas/ErrorResponse"
      }
    }
  }
};

for (const path of Object.keys(doc.paths)) {
  for (const method of Object.keys(doc.paths[path])) {
    const operation = doc.paths[path][method];
    if (operation.responses && operation.responses['200']) {
      // Check if it has any 4xx response
      const has4xx = Object.keys(operation.responses).some(code => code.startsWith('4'));
      if (!has4xx) {
        operation.responses['400'] = errorResponseRef;
        operation.responses['404'] = errorResponse404Ref;
      }
    }
  }
}

fs.writeFileSync(openapiPath, yaml.dump(doc));
console.log('Fixed openapi.yaml');
