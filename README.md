# Andy's Bot Server

## List pizzas

`GET /andys/pizzas?lang=(en|ru|ro)`

## Get salads

`GET /andys/salads?lang=(en|ru|ro)`

## Add item to cart

`POST /andys/cart`

Body:

```json
{
  "item": 269,
  "user": "069123456",
  "channel": "telegram"
}
```

## Add contact information to order

`POST /andys/cart/contact`

Body:

```json
{
  "user": "069123456",
  "client": "telegram",
  "contact": {
    "name": "Ion Doe",
    "street": "Stefan cel Mare",
    "house": 202,
    "phone": "+373 (69) 123456",
    "city": 2
  }
}
```

## Reset cart

`DELETE /andys/cart/`

Body:

```json
{
  "user": "069123456",
  "channel": "telegram"
}
```

## Checkout order

`POST /andys/order/checkout?lang=(en|ru|ro)`

Body:

```json
{
  "user": "069123456",
  "channel": "telegram"
}
```

Example response:

```json
{
  "Дьябло (3)": "240 MDL",
  "Заказ": "240.00 MDL",
  "Доставка": "0.00 MDL",
  "Скидка": "7%",
  "Итого": "223.20 MDL"
}
```

## Place order

`POST /andys/order/place`

Body:

```json
{
  "user": "069123456",
  "channel": "telegram"
}
```
