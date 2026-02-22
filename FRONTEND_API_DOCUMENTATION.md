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
| `/api/auth/register` | POST | ✅ **Evet** (Bearer token) |
| `/api/auth/login` | POST | ✅ **Evet** (Bearer token) |
| `/api/auth/me` | GET | ✅ **Evet** |
| `/api/auth/me` | PUT | ✅ **Evet** |
| `/api/auth/me` | DELETE | ✅ **Evet** |
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
| `/api/reviews` | GET | ❌ **Hayır** (Public) |
| `/api/reviews` | POST | ✅ **Evet** |
| `/api/reviews/{id}` | GET | ❌ **Hayır** (Public) |
| `/api/reviews/{id}` | PUT | ✅ **Evet** |
| `/api/reviews/{id}` | DELETE | ✅ **Evet** |
| `/api/reviews/product/{productId}` | GET | ❌ **Hayır** (Public) |
| `/api/reviews/user/{userId}` | GET | ❌ **Hayır** (Public) |
| `/api/interactions/review/{reviewId}/like` | POST | ✅ **Evet** |
| `/api/interactions/product/{productId}/like` | POST | ✅ **Evet** |
| `/api/interactions/review/{reviewId}/like-count` | GET | ❌ **Hayır** (Public) |
| `/api/interactions/review/{reviewId}/count` | GET | ❌ **Hayır** (Public) |
| `/api/interactions/product/{productId}/like-count` | GET | ❌ **Hayır** (Public) |
| `/api/interactions/product/{productId}/count` | GET | ❌ **Hayır** (Public) |
| `/api/interactions/product/{productId}/rating` | POST | ✅ **Evet** |
| `/api/interactions/product/{productId}/average-rating` | GET | ❌ **Hayır** (Public) |
| `/api/interactions/product/{productId}/user-rating` | GET | ❌ **Hayır** (Public) |
| `/api/interactions/review/{reviewId}/is-liked` | GET | ❌ **Hayır** (Public) |
| `/api/interactions/product/{productId}/is-liked` | GET | ❌ **Hayır** (Public) |

---

## 🔐 AUTH ENDPOINT'LERİ

### 1. Kullanıcı Kaydı (Register)
Yeni kullanıcı kaydı oluşturur. Firebase'den alınan ID token ile birlikte kullanıcı bilgileri gönderilir.

**Endpoint:**
```
POST /api/auth/register
```

**Authentication:** ✅ **Gerekir** - Bearer token ile istek atılmalı

**Request Headers:**
```
Authorization: Bearer <firebase-id-token>
Content-Type: application/json
```

**Request Body (Profil fotoğrafı olmadan):**
```json
{
  "userName": "johndoe",
  "name": "John",
  "surname": "Doe",
  "birthdate": "1990-05-15"
}
```

**Request Body (Profil fotoğrafı ile - Base64):**
```json
{
  "userName": "johndoe",
  "name": "John",
  "surname": "Doe",
  "birthdate": "1990-05-15",
  "profilePhotoBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
  "profilePhotoMimeType": "image/jpeg"
}
```

**Request Body Açıklamaları:**
- `userName` (String): Kullanıcı adı (unique olmalı)
- `name` (String): Kullanıcının adı
- `surname` (String): Kullanıcının soyadı
- `birthdate` (String): Doğum tarihi (format: "YYYY-MM-DD")
- `profilePhotoBase64` (String, optional): Base64 encoded profil fotoğrafı (data URI formatında)
- `profilePhotoMimeType` (String, optional): Fotoğraf MIME type'ı (örn: "image/jpeg", "image/png")

**Response:**
```json
{
  "id": 1,
  "email": "john.doe@example.com",
  "userName": "johndoe",
  "name": "John",
  "surname": "Doe",
  "birthdate": "1990-05-15",
  "userType": "GENERAL_USER",
  "active": true,
  "profilePhotoData": "/9j/4AAQSkZJRg...",
  "profilePhotoMimeType": "image/jpeg"
}
```

**Alternatif: Multipart Form-Data ile Kayıt**

**Endpoint:**
```
POST /api/auth/register/multipart
```

**Request Headers:**
```
Authorization: Bearer <firebase-id-token>
Content-Type: multipart/form-data
```

**Form Data:**
- `userName`: "johndoe" (Text)
- `name`: "John" (Text)
- `surname`: "Doe" (Text)
- `birthdate`: "1990-05-15" (Text)
- `profilePhoto`: [FILE] (File - opsiyonel)

**Not:** Multipart endpoint'te `profilePhoto` key adı kullanılmalı, `profilePhotoBase64` değil!

**Hata Durumları:**
- `400 Bad Request`: Geçersiz request body veya validation hatası
- `401 Unauthorized`: Geçersiz veya eksik Firebase token
- `409 Conflict`: userName zaten kullanılıyorsa

---

### 2. Kullanıcı Girişi (Login)
Daha önce kayıt olmuş kullanıcılar için giriş yapar. Firebase ID token ile authentication yapılır.

**Endpoint:**
```
POST /api/auth/login
```

**Authentication:** ✅ **Gerekir** - Bearer token ile istek atılmalı

**Request Headers:**
```
Authorization: Bearer <firebase-id-token>
```

**Response:**
```json
{
  "id": 1,
  "email": "john.doe@example.com",
  "userName": "johndoe",
  "name": "John",
  "surname": "Doe",
  "birthdate": "1990-05-15",
  "userType": "GENERAL_USER",
  "active": true,
  "profilePhotoData": "/9j/4AAQSkZJRg...",
  "profilePhotoMimeType": "image/jpeg"
}
```

**Hata Durumları:**
- `401 Unauthorized`: Geçersiz Firebase token veya kullanıcı bulunamadı
- `404 Not Found`: Kullanıcı daha önce kayıt olmamışsa

---

### 3. Kullanıcı Bilgilerini Getir (Me)
Authenticated kullanıcının kendi bilgilerini getirir. SecurityContext'ten otomatik olarak kullanıcı bilgisi alınır.

**Endpoint:**
```
GET /api/auth/me
```

**Authentication:** ✅ **Gerekir** - Bearer token ile istek atılmalı

**Request Headers:**
```
Authorization: Bearer <firebase-id-token>
```

**Response:**
```json
{
  "id": 1,
  "email": "john.doe@example.com",
  "userName": "johndoe",
  "name": "John",
  "surname": "Doe",
  "birthdate": "1990-05-15",
  "userType": "GENERAL_USER",
  "active": true,
  "profilePhotoData": "/9j/4AAQSkZJRg...",
  "profilePhotoMimeType": "image/jpeg"
}
```

**Hata Durumları:**
- `401 Unauthorized`: Geçersiz veya eksik token

---

### 4. Kullanıcı Bilgilerini Güncelle (Update Me)
Authenticated kullanıcının kendi profil bilgilerini günceller. Tüm alanlar opsiyoneldir (sadece gönderilen alanlar güncellenir).

**Endpoint:**
```
PUT /api/auth/me
```

**Authentication:** ✅ **Gerekir** - Bearer token ile istek atılmalı

**Request Headers:**
```
Authorization: Bearer <firebase-id-token>
Content-Type: application/json
```

**Request Body (Profil fotoğrafı olmadan):**
```json
{
  "userName": "newusername",
  "name": "Jane",
  "surname": "Smith",
  "birthdate": "1992-08-20"
}
```

**Request Body (Profil fotoğrafı ile - Base64):**
```json
{
  "userName": "newusername",
  "name": "Jane",
  "surname": "Smith",
  "birthdate": "1992-08-20",
  "profilePhotoBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
  "profilePhotoMimeType": "image/jpeg"
}
```

**Request Body Açıklamaları:**
- Tüm alanlar **opsiyonel** - sadece güncellenmek istenen alanlar gönderilir
- `userName` (String, optional): Yeni kullanıcı adı (unique olmalı, boş olamaz)
- `name` (String, optional): Yeni ad
- `surname` (String, optional): Yeni soyad
- `birthdate` (String, optional): Yeni doğum tarihi (format: "YYYY-MM-DD", geçmiş bir tarih olmalı)
- `profilePhotoBase64` (String, optional): Base64 encoded profil fotoğrafı (data URI formatında)
- `profilePhotoMimeType` (String, optional): Fotoğraf MIME type'ı (örn: "image/jpeg", "image/png")

**Örnek - Sadece isim güncelleme:**
```json
{
  "name": "Jane"
}
```

**Örnek - Sadece profil fotoğrafı güncelleme:**
```json
{
  "profilePhotoBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
  "profilePhotoMimeType": "image/jpeg"
}
```

**Response:**
```json
{
  "id": 1,
  "email": "john.doe@example.com",
  "userName": "newusername",
  "name": "Jane",
  "surname": "Smith",
  "birthdate": "1992-08-20",
  "userType": "GENERAL_USER",
  "active": true,
  "profilePhotoData": "/9j/4AAQSkZJRg...",
  "profilePhotoMimeType": "image/jpeg"
}
```

**Alternatif: Multipart Form-Data ile Güncelleme**

**Endpoint:**
```
PUT /api/auth/me/multipart
```

**Request Headers:**
```
Authorization: Bearer <firebase-id-token>
Content-Type: multipart/form-data
```

**Form Data:**
- `userName`: "newusername" (Text - opsiyonel)
- `name`: "Jane" (Text - opsiyonel)
- `surname`: "Smith" (Text - opsiyonel)
- `birthdate`: "1992-08-20" (Text - opsiyonel)
- `profilePhoto`: [FILE] (File - opsiyonel)

**Not:** Multipart endpoint'te `profilePhoto` key adı kullanılmalı, `profilePhotoBase64` değil!

**Hata Durumları:**
- `400 Bad Request`: Geçersiz request body veya validation hatası (örn: userName boş, birthdate gelecek tarih)
- `401 Unauthorized`: Geçersiz veya eksik token
- `409 Conflict`: userName zaten başka bir kullanıcı tarafından kullanılıyorsa

**Önemli Notlar:**
- Email adresi Firebase tarafında yönetilir, bu endpoint üzerinden güncellenemez
- Sadece authenticated kullanıcı kendi profilini güncelleyebilir
- userName değiştirilirse, yeni userName unique olmalı

---

### 5. Kullanıcı Hesabını Sil (Delete Me)
Authenticated kullanıcının kendi hesabını siler (soft delete - isActive = false).

**Endpoint:**
```
DELETE /api/auth/me
```

**Authentication:** ✅ **Gerekir** - Bearer token ile istek atılmalı

**Request Headers:**
```
Authorization: Bearer <firebase-id-token>
```

**Response:**
```
204 No Content
```

**Hata Durumları:**
- `401 Unauthorized`: Geçersiz veya eksik token

**Önemli Notlar:**
- Kullanıcı fiziksel olarak silinmez, sadece `isActive = false` yapılır
- Sadece authenticated kullanıcı kendi hesabını silebilir

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

## 📝 REVIEW ENDPOINT'LERİ

### 1. Review Oluştur
Yeni review oluşturur. User bilgisi Authorization header'daki Bearer token'dan otomatik olarak alınır.

**Endpoint:**
```
POST /api/reviews
```

**Authentication:** ✅ **Gerekir** - Bearer token ile istek atılmalı

**Request Headers:**
```
Authorization: Bearer <firebase-id-token>
Content-Type: application/json
```

**Request Body (mediaList opsiyonel):**
```json
{
  "productId": 123,
  "title": "Great product!",
  "description": "Really satisfied with this product...",
  "isCollaborative": false,
  "rating": 5,
  "mediaList": [
    {
      "imageData": [base64 or binary],
      "mimeType": "image/jpeg"
    }
  ]
}
```

**Request Body (mediaList olmadan):**
```json
{
  "productId": 123,
  "title": "Great product!",
  "description": "Really satisfied with this product...",
  "isCollaborative": false,
  "rating": 5
}
```

**Request Body Açıklamaları:**
- `productId` (Long, required): Review hangi product için
- `title` (String, required): Review başlığı
- `description` (String, optional): Review açıklaması
- `isCollaborative` (Boolean, optional): Collaborative review mı? (default: false)
- `rating` (Integer, required): 1-5 arası rating
- `mediaList` (List, optional): Review'a eklenen media dosyaları (gönderilmezse review medya olmadan oluşturulur)
  - `imageData` (byte[]): Base64 veya binary image data
  - `mimeType` (String): MIME type (örn: "image/jpeg", "image/png")

**Response:**
```json
{
  "id": 1,
  "title": "Great product!",
  "description": "Really satisfied with this product...",
  "isCollaborative": false,
  "rating": 5,
  "createdAt": "2024-01-15T10:30:00",
  "productId": 123,
  "productName": "iPhone 13 Pro",
  "ownerId": 1,
  "ownerUserName": "johndoe",
  "mediaList": [
    {
      "id": 1,
      "mimeType": "image/jpeg",
      "uploadDate": "2024-01-15T10:30:00"
    }
  ],
  "likeCount": 0,
  "isLikedByCurrentUser": false
}
```

**Hata Durumları:**
- `400 Bad Request`: Product bulunamazsa veya rating geçersizse (1-5 arası olmalı)
- `401 Unauthorized`: Geçersiz veya eksik token

---

### 2. Review Getir (ID'ye Göre)
Belirli bir review'ı ID'sine göre getirir.

**Endpoint:**
```
GET /api/reviews/{id}
```

**Path Parameters:**
- `id` (Long): Review ID'si

**Authentication:** ❌ Gerekmez (Public)

**Request Headers (Opsiyonel - eğer authenticated kullanıcı varsa):**
```
Authorization: Bearer <firebase-id-token>
```

**Response:**
```json
{
  "id": 1,
  "title": "Great product!",
  "description": "Really satisfied with this product...",
  "isCollaborative": false,
  "rating": 5,
  "createdAt": "2024-01-15T10:30:00",
  "productId": 123,
  "productName": "iPhone 13 Pro",
  "ownerId": 1,
  "ownerUserName": "johndoe",
  "mediaList": [
    {
      "id": 1,
      "mimeType": "image/jpeg",
      "uploadDate": "2024-01-15T10:30:00"
    }
  ],
  "likeCount": 5,
  "isLikedByCurrentUser": true
}
```

**Hata Durumları:**
- `404 Not Found`: Review bulunamazsa veya pasifse

**Not:** Eğer authenticated kullanıcı varsa, `isLikedByCurrentUser` doğru değeri döner. Yoksa `false` döner.

---

### 3. Product'a Göre Review'ları Getir
Belirli bir product'a ait tüm aktif review'ları getirir.

**Endpoint:**
```
GET /api/reviews/product/{productId}
```

**Path Parameters:**
- `productId` (Long): Product ID'si

**Authentication:** ❌ Gerekmez (Public)

**Request Headers (Opsiyonel - eğer authenticated kullanıcı varsa):**
```
Authorization: Bearer <firebase-id-token>
```

**Response:**
```json
[
  {
    "id": 1,
    "title": "Great product!",
    "description": "Really satisfied with this product...",
    "isCollaborative": false,
    "rating": 5,
    "createdAt": "2024-01-15T10:30:00",
    "productId": 123,
    "productName": "iPhone 13 Pro",
    "ownerId": 1,
    "ownerUserName": "johndoe",
    "mediaList": [],
    "likeCount": 5,
    "isLikedByCurrentUser": false
  },
  {
    "id": 2,
    "title": "Not bad",
    "description": "Could be better...",
    "isCollaborative": false,
    "rating": 3,
    "createdAt": "2024-01-15T11:00:00",
    "productId": 123,
    "productName": "iPhone 13 Pro",
    "ownerId": 2,
    "ownerUserName": "janedoe",
    "mediaList": [],
    "likeCount": 2,
    "isLikedByCurrentUser": true
  }
]
```

---

### 4. Kullanıcıya Göre Review'ları Getir
Belirli bir kullanıcının tüm aktif review'larını getirir.

**Endpoint:**
```
GET /api/reviews/user/{userId}
```

**Path Parameters:**
- `userId` (Long): Kullanıcı ID'si

**Authentication:** ❌ Gerekmez (Public)

**Request Headers (Opsiyonel - eğer authenticated kullanıcı varsa):**
```
Authorization: Bearer <firebase-id-token>
```

**Response:**
```json
[
  {
    "id": 1,
    "title": "Great product!",
    "description": "Really satisfied with this product...",
    "isCollaborative": false,
    "rating": 5,
    "createdAt": "2024-01-15T10:30:00",
    "productId": 123,
    "productName": "iPhone 13 Pro",
    "ownerId": 1,
    "ownerUserName": "johndoe",
    "mediaList": [],
    "likeCount": 5,
    "isLikedByCurrentUser": false
  }
]
```

---

### 5. Review Güncelle
Review'ı günceller. Sadece review sahibi güncelleyebilir. Partial update: Sadece gönderilen field'lar güncellenir.

**Endpoint:**
```
PUT /api/reviews/{id}
```

**Path Parameters:**
- `id` (Long): Review ID'si

**Authentication:** ✅ **Gerekir** - Bearer token ile istek atılmalı

**Request Headers:**
```
Authorization: Bearer <firebase-id-token>
Content-Type: application/json
```

**Request Body (Tüm alanlar opsiyonel):**
```json
{
  "title": "Updated Title",
  "description": "Updated description...",
  "isCollaborative": true,
  "rating": 4,
  "mediaList": [
    {
      "imageData": [base64 or binary],
      "mimeType": "image/jpeg"
    }
  ]
}
```

**Örnek - Sadece başlık güncelleme:**
```json
{
  "title": "Updated Title"
}
```

**Response:**
```json
{
  "id": 1,
  "title": "Updated Title",
  "description": "Updated description...",
  "isCollaborative": true,
  "rating": 4,
  "createdAt": "2024-01-15T10:30:00",
  "productId": 123,
  "productName": "iPhone 13 Pro",
  "ownerId": 1,
  "ownerUserName": "johndoe",
  "mediaList": [],
  "likeCount": 5,
  "isLikedByCurrentUser": false
}
```

**Hata Durumları:**
- `401 Unauthorized`: Geçersiz veya eksik token
- `403 Forbidden`: Review sahibi değilseniz
- `404 Not Found`: Review bulunamazsa

**Not:** `mediaList` güncellenirse, eski media'lar soft delete yapılır ve yeni media'lar eklenir.

---

### 6. Review Sil
Review'ı siler (soft delete - isActive = false). Sadece review sahibi silebilir.

**Endpoint:**
```
DELETE /api/reviews/{id}
```

**Path Parameters:**
- `id` (Long): Review ID'si

**Authentication:** ✅ **Gerekir** - Bearer token ile istek atılmalı

**Request Headers:**
```
Authorization: Bearer <firebase-id-token>
```

**Response:**
```
204 No Content
```

**Hata Durumları:**
- `401 Unauthorized`: Geçersiz veya eksik token
- `403 Forbidden`: Review sahibi değilseniz
- `404 Not Found`: Review bulunamazsa veya zaten pasifse

**Önemli Notlar:**
- Review fiziksel olarak silinmez, sadece `isActive = false` yapılır
- Sadece authenticated kullanıcı kendi review'ını silebilir

---

## ❤️ INTERACTION ENDPOINT'LERİ

### 1. Review'a Like/Unlike Yap
Review'a like/unlike yapar. Eğer zaten like varsa unlike yapar, yoksa like ekler. Kendi review'ınızı beğenemezsiniz.

**Endpoint:**
```
POST /api/interactions/review/{reviewId}/like
```

**Path Parameters:**
- `reviewId` (Long): Review ID'si

**Authentication:** ✅ **Gerekir** - Bearer token ile istek atılmalı

**Request Headers:**
```
Authorization: Bearer <firebase-id-token>
```

**Response:**
```json
{
  "liked": true
}
```

veya

```json
{
  "liked": false
}
```

**Hata Durumları:**
- `400 Bad Request`: Kendi review'ınızı beğenmeye çalışırsanız
- `401 Unauthorized`: Geçersiz veya eksik token
- `404 Not Found`: Review bulunamazsa

---

### 2. Product'a Like/Unlike Yap
Product'a like/unlike yapar. Eğer zaten like varsa unlike yapar, yoksa like ekler.

**Endpoint:**
```
POST /api/interactions/product/{productId}/like
```

**Path Parameters:**
- `productId` (Long): Product ID'si

**Authentication:** ✅ **Gerekir** - Bearer token ile istek atılmalı

**Request Headers:**
```
Authorization: Bearer <firebase-id-token>
```

**Response:**
```json
{
  "liked": true
}
```

veya

```json
{
  "liked": false
}
```

**Hata Durumları:**
- `401 Unauthorized`: Geçersiz veya eksik token
- `404 Not Found`: Product bulunamazsa

---

### 3. Review'ın Like Sayısını Getir
Belirli bir review'ın like sayısını getirir.

**Endpoint:**
```
GET /api/interactions/review/{reviewId}/like-count
```

**Path Parameters:**
- `reviewId` (Long): Review ID'si

**Authentication:** ❌ Gerekmez (Public)

**Response:**
```json
{
  "count": 15
}
```

---

### 4. Product'ın Like Sayısını Getir
Belirli bir product'ın like sayısını getirir.

**Endpoint:**
```
GET /api/interactions/product/{productId}/like-count
```

**Path Parameters:**
- `productId` (Long): Product ID'si

**Authentication:** ❌ Gerekmez (Public)

**Response:**
```json
{
  "count": 42
}
```

---

### 5. Kullanıcının Review'ı Beğenip Beğenmediğini Kontrol Et
Mevcut kullanıcının belirli bir review'ı beğenip beğenmediğini kontrol eder.

**Endpoint:**
```
GET /api/interactions/review/{reviewId}/is-liked
```

**Path Parameters:**
- `reviewId` (Long): Review ID'si

**Authentication:** ❌ Gerekmez (Public)

**Request Headers (Opsiyonel - eğer authenticated kullanıcı varsa):**
```
Authorization: Bearer <firebase-id-token>
```

**Response:**
```json
{
  "isLiked": true
}
```

veya

```json
{
  "isLiked": false
}
```

**Not:** Eğer kullanıcı authenticated değilse veya review'ı beğenmemişse `false` döner.

---

### 6. Kullanıcının Product'ı Beğenip Beğenmediğini Kontrol Et
Mevcut kullanıcının belirli bir product'ı beğenip beğenmediğini kontrol eder.

**Endpoint:**
```
GET /api/interactions/product/{productId}/is-liked
```

**Path Parameters:**
- `productId` (Long): Product ID'si

**Authentication:** ❌ Gerekmez (Public)

**Request Headers (Opsiyonel - eğer authenticated kullanıcı varsa):**
```
Authorization: Bearer <firebase-id-token>
```

**Response:**
```json
{
  "isLiked": true
}
```

veya

```json
{
  "isLiked": false
}
```

**Not:** Eğer kullanıcı authenticated değilse veya product'ı beğenmemişse `false` döner.

---

### 7. Review'a Yapılan Type'a Göre Interaction Sayısını Getir
Belirli bir review'a yapılan belirli type'taki interaction sayısını getirir.

**Endpoint:**
```
GET /api/interactions/review/{reviewId}/count?type={type}
```

**Path Parameters:**
- `reviewId` (Long): Review ID'si

**Query Parameters:**
- `type` (String, required): Interaction type (LIKE, DISLIKE, REPORT, vb.)

**Authentication:** ❌ Gerekmez (Public)

**Örnek İstekler:**
```
GET /api/interactions/review/1/count?type=LIKE
GET /api/interactions/review/1/count?type=DISLIKE
```

**Response:**
```json
{
  "count": 15,
  "type": "LIKE"
}
```

---

### 8. Product'a Yapılan Type'a Göre Interaction Sayısını Getir
Belirli bir product'a yapılan belirli type'taki interaction sayısını getirir.

**Endpoint:**
```
GET /api/interactions/product/{productId}/count?type={type}
```

**Path Parameters:**
- `productId` (Long): Product ID'si

**Query Parameters:**
- `type` (String, required): Interaction type (LIKE, WISHLIST, RATING, vb.)

**Authentication:** ❌ Gerekmez (Public)

**Örnek İstekler:**
```
GET /api/interactions/product/1/count?type=LIKE
GET /api/interactions/product/1/count?type=WISHLIST
GET /api/interactions/product/1/count?type=RATING
```

**Response:**
```json
{
  "count": 42,
  "type": "LIKE"
}
```

---

### 9. Product'a Rating Ver (1-5 Arası Yıldız Sistemi)
Product'a rating verir (1-5 arası). Eğer kullanıcı daha önce rating vermişse günceller.

**Endpoint:**
```
POST /api/interactions/product/{productId}/rating
```

**Path Parameters:**
- `productId` (Long): Product ID'si

**Authentication:** ✅ **Gerekir** - Bearer token ile istek atılmalı

**Request Headers:**
```
Authorization: Bearer <firebase-id-token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "rating": 4
}
```

**Request Body Açıklamaları:**
- `rating` (Integer, required): 1-5 arası rating değeri

**Response:**
```json
{
  "rating": 4
}
```

**Hata Durumları:**
- `400 Bad Request`: Rating 1-5 arası değilse
- `401 Unauthorized`: Geçersiz veya eksik token
- `404 Not Found`: Product bulunamazsa

**Önemli Notlar:**
- Rating sadece Product'lar için geçerlidir (Review'lar için değil)
- Eğer kullanıcı daha önce rating vermişse, yeni rating eski rating'i günceller
- Rating interaction type'ı "RATING" olarak kaydedilir

---

### 10. Product'ın Ortalama Rating'ini Getir
Belirli bir product'ın ortalama rating'ini getirir (tüm kullanıcıların verdiği rating'lerin ortalaması).

**Endpoint:**
```
GET /api/interactions/product/{productId}/average-rating
```

**Path Parameters:**
- `productId` (Long): Product ID'si

**Authentication:** ❌ Gerekmez (Public)

**Response:**
```json
{
  "averageRating": 4.5,
  "productId": 123
}
```

**Not:** Eğer hiç rating verilmemişse, `averageRating` 0.0 döner.

---

### 11. Kullanıcının Product'a Verdiği Rating'i Getir
Mevcut kullanıcının belirli bir product'a verdiği rating'i getirir.

**Endpoint:**
```
GET /api/interactions/product/{productId}/user-rating
```

**Path Parameters:**
- `productId` (Long): Product ID'si

**Authentication:** ❌ Gerekmez (Public)

**Request Headers (Opsiyonel - eğer authenticated kullanıcı varsa):**
```
Authorization: Bearer <firebase-id-token>
```

**Response (Rating vermişse):**
```json
{
  "rating": 4
}
```

**Response (Rating vermemişse):**
```json
{
  "rating": null
}
```

**Not:** Eğer kullanıcı authenticated değilse veya rating vermemişse `null` döner.

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

### 5. Product Interaction Type'ları
Product'lar için kullanılan interaction type'ları:
- `LIKE`: Product'ı beğenme
- `WISHLIST`: Product'ı wishlist'e ekleme
- `RATING`: Product'a rating verme (1-5 arası, rating field'ında saklanır)

**Önemli:** Rating sadece Product'lar için geçerlidir. Review'lar için rating yoktur (Review entity'sinde zaten rating field'ı var).

### 6. Review Interaction Type'ları
Review'lar için kullanılan interaction type'ları:
- `LIKE`: Review'ı beğenme
- `DISLIKE`: Review'ı beğenmeme
- `REPORT`: Review'ı şikayet etme

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

### Kullanıcı Kaydı (Register) - Authentication Gerekir
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <firebase-id-token>" \
  -d '{
    "userName": "johndoe",
    "name": "John",
    "surname": "Doe",
    "birthdate": "1990-05-15",
    "profilePhotoBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
    "profilePhotoMimeType": "image/jpeg"
  }'
```

### Kullanıcı Kaydı (Multipart) - Authentication Gerekir
```bash
curl -X POST http://localhost:8080/api/auth/register/multipart \
  -H "Authorization: Bearer <firebase-id-token>" \
  -F "userName=johndoe" \
  -F "name=John" \
  -F "surname=Doe" \
  -F "birthdate=1990-05-15" \
  -F "profilePhoto=@/path/to/photo.jpg"
```

### Kullanıcı Girişi (Login) - Authentication Gerekir
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Authorization: Bearer <firebase-id-token>"
```

### Kullanıcı Bilgilerini Getir (Me) - Authentication Gerekir
```bash
curl -X GET http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer <firebase-id-token>"
```

### Kullanıcı Bilgilerini Güncelle (Update Me) - Authentication Gerekir
```bash
curl -X PUT http://localhost:8080/api/auth/me \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <firebase-id-token>" \
  -d '{
    "userName": "newusername",
    "name": "Jane",
    "surname": "Smith",
    "birthdate": "1992-08-20",
    "profilePhotoBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
    "profilePhotoMimeType": "image/jpeg"
  }'
```

### Kullanıcı Bilgilerini Güncelle (Multipart) - Authentication Gerekir
```bash
curl -X PUT http://localhost:8080/api/auth/me/multipart \
  -H "Authorization: Bearer <firebase-id-token>" \
  -F "userName=newusername" \
  -F "name=Jane" \
  -F "surname=Smith" \
  -F "birthdate=1992-08-20" \
  -F "profilePhoto=@/path/to/photo.jpg"
```

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

### Review Oluştur (mediaList olmadan) - Authentication Gerekir
```bash
curl -X POST http://localhost:8080/api/reviews \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <firebase-id-token>" \
  -d '{
    "productId": 123,
    "title": "Great product!",
    "description": "Really satisfied with this product...",
    "isCollaborative": false,
    "rating": 5
  }'
```

### Product'a Göre Review'ları Getir (Public)
```bash
curl -X GET http://localhost:8080/api/reviews/product/123
```

### Review'a Like Yap - Authentication Gerekir
```bash
curl -X POST http://localhost:8080/api/interactions/review/1/like \
  -H "Authorization: Bearer <firebase-id-token>"
```

### Review Like Sayısını Getir (Public)
```bash
curl -X GET http://localhost:8080/api/interactions/review/1/like-count
```

### Kullanıcının Review'ı Beğenip Beğenmediğini Kontrol Et (Public)
```bash
curl -X GET http://localhost:8080/api/interactions/review/1/is-liked \
  -H "Authorization: Bearer <firebase-id-token>"
```

### Review Type'a Göre Count Getir (Public)
```bash
curl -X GET "http://localhost:8080/api/interactions/review/1/count?type=LIKE"
```

### Product Type'a Göre Count Getir (Public)
```bash
curl -X GET "http://localhost:8080/api/interactions/product/1/count?type=LIKE"
```

### Product'a Rating Ver - Authentication Gerekir
```bash
curl -X POST http://localhost:8080/api/interactions/product/1/rating \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <firebase-id-token>" \
  -d '{
    "rating": 4
  }'
```

### Product Ortalama Rating Getir (Public)
```bash
curl -X GET http://localhost:8080/api/interactions/product/1/average-rating
```

### Kullanıcının Product Rating'ini Getir (Public)
```bash
curl -X GET http://localhost:8080/api/interactions/product/1/user-rating \
  -H "Authorization: Bearer <firebase-id-token>"
```

---

**Son Güncelleme:** 2024-01-15

---

## 📋 Changelog

### 2024-01-15
- ✅ Review endpoint'leri eklendi (GET, POST, PUT, DELETE)
- ✅ Interaction endpoint'leri eklendi (Like/Unlike, Like Count, Is Liked)
- ✅ DELETE /api/auth/me endpoint'i eklendi
- ✅ Authentication tablosu güncellendi
- ✅ Type'a göre interaction count endpoint'leri eklendi (Review ve Product için)
  - GET /api/interactions/review/{reviewId}/count?type={type}
  - GET /api/interactions/product/{productId}/count?type={type}
- ✅ Product rating sistemi eklendi (1-5 arası yıldız sistemi)
  - POST /api/interactions/product/{productId}/rating - Rating ver/güncelle
  - GET /api/interactions/product/{productId}/average-rating - Ortalama rating
  - GET /api/interactions/product/{productId}/user-rating - Kullanıcının rating'i
- ✅ Profil fotoğrafı desteği eklendi
  - POST /api/auth/register - Profil fotoğrafı ile kayıt (Base64 veya multipart)
  - POST /api/auth/register/multipart - Multipart form-data ile kayıt
  - PUT /api/auth/me - Profil fotoğrafı güncelleme (Base64 veya multipart)
  - PUT /api/auth/me/multipart - Multipart form-data ile güncelleme
  - GET /api/auth/me - Response'da profil fotoğrafı bilgisi
  - Response'larda `profilePhotoData` ve `profilePhotoMimeType` alanları eklendi

