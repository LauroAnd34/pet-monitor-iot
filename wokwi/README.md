# Pinagem no Wokwi

O arquivo `diagram.json` reúne as duas ESP32 do Pet Guardian IoT em uma única visão.
Importe-o em um projeto Wokwi para consultar e revisar a pinagem.

## Abstrações usadas

- O DHT22 representa o DHT11 porque possui a mesma finalidade e ligação de sinal.
- Potenciômetros representam as saídas analógicas do sensor de gás e do LDR.
- Analisadores lógicos representam a OV7670, a ponte H e demais saídas sem componente
  equivalente completo no simulador.
- Os HC-SR04 e o PIR são componentes simuláveis.

As abstrações servem para visualizar conexões. Elas não substituem testes na montagem
real e não exigem nenhuma mudança nos firmwares.

