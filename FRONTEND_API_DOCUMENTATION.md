# 🚀 Frontend API Dokümantasyonu

## Base URL
```
http://localhost:8080/api
```

---

## 🔐 Authentication

**ÖNEMLİ:** 
- **Tag endpoint'leri** (search hariç) **authentication gerektirir** - Bearer token ile istek atılmalı
- **Product endpoint'leri** şu an **authentication gerektirmiyor** (test için)
- **Tag search endpoint'i** (`/api/tags/search`) **public** - authentication gerektirmez

Eğer authentication gerektiren bir endpoint'e istek atıyorsanız:
```
Authorization: Bearer <firebase-id-token>
```

**Örnek:**
```javascript
fetch('/api/tags/roots', {
  headers: {
    'Authorization': `Bearer ${firebaseIdToken}`,
    'Content-Type': 'application/json'
  }
})
```

### 📋 Authentication Özet Tablosu

| Endpoint | Method | Authentication Gerekir? |
|----------|--------|------------------------|
| `/api/tags/roots` | GET | ✅ **Evet** |
| `/api/tags/{id}/children` | GET | ✅ **Evet** |
| `/api/tags/search` | GET | ❌ **Hayır** (Public) |
| `/api/tags` | POST | ✅ **Evet** |
| `/api/tags/tree` | GET | ✅ **Evet** |
| `/api/tags/{id}` | GET | ✅ **Evet** |
| `/api/tags/path` | GET | ✅ **Evet** |
| `/api/products` | GET, POST | ❌ **Hayır** (Test için) |
| `/api/products/{id}` | GET | ❌ **Hayır** (Test için) |
| `/api/products/tag/{tagId}` | GET | ❌ **Hayır** (Test için) |

---

## 🏷️ TAG ENDPOINT'LERİ

### 1. Root Tag'leri Getir (İlk Adım)
Kullanıcı kategori sayfasına girdiğinde ilk çağrılacak endpoint. Sadece parent'ı olmayan (root) tag'leri döner.

**Endpoint:**
```
GET /api/tags/roots
```

**Authentication:** ✅ **Gerekir** - Bearer token ile istek atılmalı

**Response:**
```json
[
  {
    "id": 1,
    "name": "Electronics",
    "categoryPath": "Electronics",
    "parentId": null,
    "children": []
  },
  {
    "id": 2,
    "name": "Books",
    "categoryPath": "Books",
    "parentId": null,
    "children": []
  }
]
```

**Kullanım Senaryosu:**
1. Kullanıcı kategori sayfasına girer
2. Bu endpoint çağrılır
3. Root tag'ler listelenir (örn: "Electronics", "Books", "Clothing")
4. Kullanıcı bir tag'e tıklar → `/api/tags/{id}/children` çağrılır

---

### 2. Tag'in Child'larını ve Product'larını Getir (Adım Adım Navigation)
Tag'in child'larını getirir. Eğer tag leaf (child'ı yok) ise, o tag'e ait product'ları döner.

**Endpoint:**
```
GET /api/tags/{id}/children
```

**Path Parameters:**
- `id` (Long): Tag ID'si

**Authentication:** ❌ Gerekmez

**Response Formatları:**

**A) Tag'in child'ı varsa (leaf değil):**
```json
{
  "id": 5,
  "name": "Telephone",
  "categoryPath": "Electronics.Telephone",
  "parentId": 1,
  "children": [
    {
      "id": 10,
      "name": "MobilePhone",
      "categoryPath": "Electronics.Telephone.MobilePhone",
      "parentId": 5,
      "children": []
    },
    {
      "id": 11,
      "name": "Landline",
      "categoryPath": "Electronics.Telephone.Landline",
      "parentId": 5,
      "children": []
    }
  ],
  "products": [],
  "isLeaf": false
}
```

**B) Tag'in child'ı yoksa (leaf tag - product'lar gelir):**
```json
{
  "id": 15,
  "name": "Iphone13",
  "categoryPath": "Electronics.Telephone.MobilePhone.Iphone.Iphone13",
  "parentId": 14,
  "children": [],
  "products": [
    {
      "id": 1,
      "name": "iPhone 13 Pro 128GB",
      "description": "Apple iPhone 13 Pro 128GB Space Gray",
      "imageURL": "https://example.com/iphone13.jpg",
      "tag": {
        "id": 15,
        "name": "Iphone13",
        "categoryPath": "Electronics.Telephone.MobilePhone.Iphone.Iphone13",
        "parentId": 14,
        "children": []
      },
      "createdAt": "2024-01-15T10:30:00",
      "isActive": true
    },
    {
      "id": 2,
      "name": "iPhone 13 256GB",
      "description": "Apple iPhone 13 256GB Blue",
      "imageURL": "https://example.com/iphone13-2.jpg",
      "tag": {
        "id": 15,
        "name": "Iphone13",
        "categoryPath": "Electronics.Telephone.MobilePhone.Iphone.Iphone13",
        "parentId": 14,
        "children": []
      },
      "createdAt": "2024-01-15T11:00:00",
      "isActive": true
    }
  ],
  "isLeaf": true
}
```

**Kullanım Senaryosu:**
1. Kullanıcı "Electronics" tag'ine tıklar → `GET /api/tags/1/children`
2. Child tag'ler gelir → "Telephone", "Computers" gösterilir
3. Kullanıcı "Telephone" → "MobilePhone" → "Iphone" → "Iphone13" derinliğine gider
4. "Iphone13" leaf tag ise → `isLeaf: true` ve `products` dolu gelir
5. Product listesi gösterilir

**Frontend Mantığı:**
```javascript
if (response.isLeaf) {
  // Product listesi göster
  displayProducts(response.products);
} else {
  // Child tag'leri göster
  displayTags(response.children);
}
```

---

### 3. Tag Arama (Public - Authentication Gerekmez)
Tag ismi veya categoryPath'te arama yapar (case-insensitive).

**Endpoint:**
```
GET /api/tags/search?name={searchTerm}
```

**Query Parameters:**
- `name` (String, optional): Arama terimi (boş string ise tüm tag'ler döner)

**Authentication:** ❌ Gerekmez

**Örnek İstekler:**
```
GET /api/tags/search?name=iPhone
GET /api/tags/search?name=Telefon
GET /api/tags/search?name=Elektronik
```

**Response:**
```json
[
  {
    "id": 15,
    "name": "Iphone13",
    "categoryPath": "Electronics.Telephone.MobilePhone.Iphone.Iphone13",
    "parentId": 14,
    "children": []
  },
  {
    "id": 16,
    "name": "Iphone14",
    "categoryPath": "Electronics.Telephone.MobilePhone.Iphone.Iphone14",
    "parentId": 14,
    "children": []
  }
]
```

**Not:** Response'da `children` her zaman boş array olarak gelir (sadece temel tag bilgileri).

---

### 4. Tag Oluştur (Admin/Test için)
Yeni tag oluşturur. Root tag için `parentId` null gönderilir.

**Endpoint:**
```
POST /api/tags
```

**Authentication:** ✅ **Gerekir** - Bearer token ile istek atılmalı

**Request Body:**
```json
{
  "name": "Iphone13",
  "parentId": 14
}
```

**Root tag oluşturmak için:**
```json
{
  "name": "Electronics",
  "parentId": null
}
```

**Response:**
```json
{
  "id": 15,
  "name": "Iphone13",
  "categoryPath": "Electronics.Telephone.MobilePhone.Iphone.Iphone13",
  "parentId": 14,
  "children": []
}
```

---

### 5. Tüm Tag'leri Getir (Tree Yapısında)
Tüm tag'leri tree yapısında (recursive children ile) getirir.

**Endpoint:**
```
GET /api/tags/tree
```

**Authentication:** ✅ **Gerekir** - Bearer token ile istek atılmalı

**Response:**
```json
[
  {
    "id": 1,
    "name": "Electronics",
    "categoryPath": "Electronics",
    "parentId": null,
    "children": [
      {
        "id": 5,
        "name": "Telephone",
        "categoryPath": "Electronics.Telephone",
        "parentId": 1,
        "children": [
          {
            "id": 10,
            "name": "MobilePhone",
            "categoryPath": "Electronics.Telephone.MobilePhone",
            "parentId": 5,
            "children": []
          }
        ]
      }
    ]
  }
]
```

**⚠️ Dikkat:** Bu endpoint tüm tag tree'sini döner, büyük veri setlerinde performans sorunu yaratabilir. Adım adım navigation için `/api/tags/roots` ve `/api/tags/{id}/children` kullanılmalı.

---

### 6. ID'ye Göre Tag Getir
Belirli bir tag'i ID'sine göre getirir (children ile birlikte).

**Endpoint:**
```
GET /api/tags/{id}
```

**Path Parameters:**
- `id` (Long): Tag ID'si

**Authentication:** ✅ **Gerekir** - Bearer token ile istek atılmalı

**Response:**
```json
{
  "id": 15,
  "name": "Iphone13",
  "categoryPath": "Electronics.Telephone.MobilePhone.Iphone.Iphone13",
  "parentId": 14,
  "children": []
}
```

---

### 7. Category Path'e Göre Tag Getir
Category path'e göre tag getirir.

**Endpoint:**
```
GET /api/tags/path?categoryPath={path}
```

**Query Parameters:**
- `categoryPath` (String, required): Tag'in category path'i (örn: "Electronics.Telephone.MobilePhone")

**Authentication:** ✅ **Gerekir** - Bearer token ile istek atılmalı

**Response:**
```json
{
  "id": 10,
  "name": "MobilePhone",
  "categoryPath": "Electronics.Telephone.MobilePhone",
  "parentId": 5,
  "children": []
}
```

---

## 📦 PRODUCT ENDPOINT'LERİ

### 1. Product Oluştur
Yeni product oluşturur. **ÖNEMLİ:** Product sadece leaf tag'lere (child'ı olmayan tag'lere) bağlanabilir.

**Endpoint:**
```
POST /api/products
```

**Authentication:** ❌ Gerekmez (şu an)

**Request Body:**
```json
{
  "name": "iPhone 13 Pro 128GB",
  "description": "Apple iPhone 13 Pro 128GB Space Gray",
  "imageURL": "https://example.com/iphone13.jpg",
  "tagId": 15
}
```

**Response:**
```json
{
  "id": 1,
  "name": "iPhone 13 Pro 128GB",
  "description": "Apple iPhone 13 Pro 128GB Space Gray",
  "imageURL": "https://example.com/iphone13.jpg",
  "tag": {
    "id": 15,
    "name": "Iphone13",
    "categoryPath": "Electronics.Telephone.MobilePhone.Iphone.Iphone13",
    "parentId": 14,
    "children": []
  },
  "createdAt": "2024-01-15T10:30:00",
  "isActive": true
}
```

**Hata Durumları:**
- `400 Bad Request`: Tag leaf tag değilse veya tag bulunamazsa
- `400 Bad Request`: Gerekli field'lar eksikse

---

### 2. Tüm Product'ları Getir
Tüm aktif product'ları listeler.

**Endpoint:**
```
GET /api/products
```

**Authentication:** ❌ Gerekmez

**Response:**
```json
[
  {
    "id": 1,
    "name": "iPhone 13 Pro 128GB",
    "description": "Apple iPhone 13 Pro 128GB Space Gray",
    "imageURL": "https://example.com/iphone13.jpg",
    "tag": {
      "id": 15,
      "name": "Iphone13",
      "categoryPath": "Electronics.Telephone.MobilePhone.Iphone.Iphone13",
      "parentId": 14,
      "children": []
    },
    "createdAt": "2024-01-15T10:30:00",
    "isActive": true
  },
  {
    "id": 2,
    "name": "iPhone 13 256GB",
    "description": "Apple iPhone 13 256GB Blue",
    "imageURL": "https://example.com/iphone13-2.jpg",
    "tag": {
      "id": 15,
      "name": "Iphone13",
      "categoryPath": "Electronics.Telephone.MobilePhone.Iphone.Iphone13",
      "parentId": 14,
      "children": []
    },
    "createdAt": "2024-01-15T11:00:00",
    "isActive": true
  }
]
```

---

### 3. ID'ye Göre Product Getir
Belirli bir product'ı ID'sine göre getirir.

**Endpoint:**
```
GET /api/products/{id}
```

**Path Parameters:**
- `id` (Long): Product ID'si

**Authentication:** ❌ Gerekmez

**Response:**
```json
{
  "id": 1,
  "name": "iPhone 13 Pro 128GB",
  "description": "Apple iPhone 13 Pro 128GB Space Gray",
  "imageURL": "https://example.com/iphone13.jpg",
  "tag": {
    "id": 15,
    "name": "Iphone13",
    "categoryPath": "Electronics.Telephone.MobilePhone.Iphone.Iphone13",
    "parentId": 14,
    "children": []
  },
  "createdAt": "2024-01-15T10:30:00",
  "isActive": true
}
```

**Hata Durumları:**
- `404 Not Found`: Product bulunamazsa veya pasifse

---

### 4. Tag'e Göre Product'ları Getir
Belirli bir tag'e ait tüm aktif product'ları getirir.

**Endpoint:**
```
GET /api/products/tag/{tagId}
```

**Path Parameters:**
- `tagId` (Long): Tag ID'si

**Authentication:** ❌ Gerekmez

**Response:**
```json
[
  {
    "id": 1,
    "name": "iPhone 13 Pro 128GB",
    "description": "Apple iPhone 13 Pro 128GB Space Gray",
    "imageURL": "https://example.com/iphone13.jpg",
    "tag": {
      "id": 15,
      "name": "Iphone13",
      "categoryPath": "Electronics.Telephone.MobilePhone.Iphone.Iphone13",
      "parentId": 14,
      "children": []
    },
    "createdAt": "2024-01-15T10:30:00",
    "isActive": true
  },
  {
    "id": 2,
    "name": "iPhone 13 256GB",
    "description": "Apple iPhone 13 256GB Blue",
    "imageURL": "https://example.com/iphone13-2.jpg",
    "tag": {
      "id": 15,
      "name": "Iphone13",
      "categoryPath": "Electronics.Telephone.MobilePhone.Iphone.Iphone13",
      "parentId": 14,
      "children": []
    },
    "createdAt": "2024-01-15T11:00:00",
    "isActive": true
  }
]
```

---

## 📝 ÖNEMLİ NOTLAR

### 1. Leaf Tag Kuralı
- Product'lar **sadece leaf tag'lere** (child'ı olmayan tag'lere) bağlanabilir
- Örnek: `Electronics.Telephone.MobilePhone.Iphone.Iphone13` → ✅ Product bağlanabilir
- Örnek: `Electronics.Telephone` → ❌ Product bağlanamaz (child'ı var)

### 2. Adım Adım Navigation Önerisi
Büyük veri setlerinde performans için:
1. İlk adım: `GET /api/tags/roots` → Root tag'leri göster
2. Kullanıcı tıklayınca: `GET /api/tags/{id}/children` → Child tag'leri veya product'ları göster
3. `isLeaf: true` ise → Product listesi göster
4. `isLeaf: false` ise → Child tag'leri göster, kullanıcı tekrar tıklayabilir

### 3. Tag Arama
- `GET /api/tags/search?name={term}` → Public endpoint, authentication gerektirmez
- Case-insensitive arama yapar
- Tag ismi veya categoryPath'te arama yapar

### 4. Response Formatları
- Tüm tarih/saat alanları ISO 8601 formatında: `"2024-01-15T10:30:00"`
- `isActive` field'ı boolean olarak gelir
- `parentId` null olabilir (root tag'ler için)

---

## 🔄 Örnek Frontend Akışı

### Senaryo: Kullanıcı iPhone 13 Product'larını Bulmak İstiyor

```javascript
// Firebase ID token'ı al (örnek - gerçek implementasyon Firebase SDK kullanır)
const firebaseIdToken = await getFirebaseIdToken(); // Kendi Firebase token alma fonksiyonunuz

// 1. Root tag'leri getir (authentication gerektirir)
const rootTags = await fetch('/api/tags/roots', {
  headers: {
    'Authorization': `Bearer ${firebaseIdToken}`,
    'Content-Type': 'application/json'
  }
}).then(r => r.json());
// Kullanıcıya göster: ["Electronics", "Books", "Clothing"]

// 2. Kullanıcı "Electronics" tıkladı (authentication gerektirir)
const electronics = await fetch('/api/tags/1/children', {
  headers: {
    'Authorization': `Bearer ${firebaseIdToken}`,
    'Content-Type': 'application/json'
  }
}).then(r => r.json());
// Kullanıcıya göster: ["Telephone", "Computers", "TV"]

// 3. Kullanıcı "Telephone" tıkladı (authentication gerektirir)
const telephone = await fetch('/api/tags/5/children', {
  headers: {
    'Authorization': `Bearer ${firebaseIdToken}`,
    'Content-Type': 'application/json'
  }
}).then(r => r.json());
// Kullanıcıya göster: ["MobilePhone", "Landline"]

// 4. Kullanıcı "MobilePhone" tıkladı (authentication gerektirir)
const mobilePhone = await fetch('/api/tags/10/children', {
  headers: {
    'Authorization': `Bearer ${firebaseIdToken}`,
    'Content-Type': 'application/json'
  }
}).then(r => r.json());
// Kullanıcıya göster: ["Iphone", "Samsung", "Xiaomi"]

// 5. Kullanıcı "Iphone" tıkladı (authentication gerektirir)
const iphone = await fetch('/api/tags/14/children', {
  headers: {
    'Authorization': `Bearer ${firebaseIdToken}`,
    'Content-Type': 'application/json'
  }
}).then(r => r.json());
// Kullanıcıya göster: ["Iphone13", "Iphone14", "Iphone15"]

// 6. Kullanıcı "Iphone13" tıkladı (authentication gerektirir)
const iphone13 = await fetch('/api/tags/15/children', {
  headers: {
    'Authorization': `Bearer ${firebaseIdToken}`,
    'Content-Type': 'application/json'
  }
}).then(r => r.json());
// isLeaf: true → Product listesi göster
if (iphone13.isLeaf) {
  displayProducts(iphone13.products);
  // Gösterilen product'lar:
  // - iPhone 13 Pro 128GB
  // - iPhone 13 256GB
  // - iPhone 13 Mini
}
```

---

## ❌ Hata Yönetimi

### Genel Hata Formatı
```json
{
  "error": "ERROR_CODE",
  "message": "Hata mesajı"
}
```

### Yaygın Hata Kodları
- `400 Bad Request`: Geçersiz request body veya validation hatası
- `404 Not Found`: Kayıt bulunamadı
- `401 Unauthorized`: Authentication gerekiyor (tag endpoint'leri için gerekir, product endpoint'leri için şu an gerekmez)

---

## 🧪 Test İçin Örnek Request'ler

### Tag Oluştur (Root) - Authentication Gerekir
```bash
curl -X POST http://localhost:8080/api/tags \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <firebase-id-token>" \
  -d '{
    "name": "Electronics",
    "parentId": null
  }'
```

### Tag Oluştur (Child) - Authentication Gerekir
```bash
curl -X POST http://localhost:8080/api/tags \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <firebase-id-token>" \
  -d '{
    "name": "Telephone",
    "parentId": 1
  }'
```

### Root Tag'leri Getir - Authentication Gerekir
```bash
curl -X GET http://localhost:8080/api/tags/roots \
  -H "Authorization: Bearer <firebase-id-token>"
```

### Tag Search - Authentication Gerekmez (Public)
```bash
curl -X GET "http://localhost:8080/api/tags/search?name=iPhone"
```

### Product Oluştur
```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "iPhone 13 Pro 128GB",
    "description": "Apple iPhone 13 Pro 128GB Space Gray",
    "imageURL": "https://example.com/iphone13.jpg",
    "tagId": 15
  }'
```

---

**Son Güncelleme:** 2024-01-15

