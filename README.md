# Repository Helper

![Repository Helper Logo](logo.jpg)

[![JitPack build status](https://jitpack.io/v/quwac/repository-helper.svg)](https://jitpack.io/#quwac/repository-helper) [![Bitrise Build Status](https://app.bitrise.io/app/600d111cb8cf6486/status.svg?token=v0ogK-RWGeX2cO32tbtExA&branch=main)](https://app.bitrise.io/app/600d111cb8cf6486)

[Repository-pattern](https://deviq.com/repository-pattern/) helper for Android Kotlin Flow.

## Setup

1. Add it in your root build.gradle at the end of repositories:

```groovy
  allprojects {
    repositories {
      ...
      maven { url 'https://jitpack.io' }
    }
  }
```

2. Add the dependency

```groovy
  dependencies {
    implementation 'com.github.quwac:repository-helper:0.4.0'
  }
```

## How to use

First, prepare Your DAO classes.

In below case using [Room](https://developer.android.com/training/data-storage/room) for local DB and [Retrofit2](https://square.github.io/retrofit/) for API client.

```kotlin

// ========== Local DB ==========

// Entity for Local DB
@Entity
data class RoomUser(
    @PrimaryKey
    val id: Long,
    @ColumnInfo
    val name: String
)

// DAO for Local DB
@Dao
interface UserDbDao {
    @Query("SELECT * FROM RoomUser")
    fun getUserAll(): Flow<List<RoomUser>>

    @Query("SELECT * FROM RoomUser")
    suspend fun getUserAllSuspend(): List<RoomUser>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg users: RoomUser)

    @Query("DELETE FROM RoomUser")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(vararg user: RoomUser) {
        deleteAll()
        upsert(*user)
    }
}

// ========== API Client ==========

// Entity for API
data class ApiUser(
    val id: Long,
    val name: String
)

// API Client
interface UserApiDao {
    @GET("/users")
    suspend fun getAllUser(): List<ApiUser>
}
```

Second, wrap your DAO classes using `NoQueryCacheDaoWrapper` and `NoQuerySelectOnlyServerDaoWrapper` in your repository class.

```kotlin
interface UserRepositoryContract {
    fun getAllUser(): Flow<List<RoomUser>>
    suspend fun refreshUserAll()
}

class UserRepository(
    private val dbDao: UserDbDao,
    private val apiDao: UserApiDao
) : UserRepositoryContract {
+   private val allCacheDaoWrapper: NoQueryCacheDaoWrapper =
+       object : NoQueryCacheDaoWrapper<List<RoomUser>, List<RoomUser>> {
+           override suspend fun deleteAll() {
+               dbDao.deleteAll()
+           }
+
+           override fun selectFlow(): Flow<List<RoomUser>> {
+               return dbDao.getUserAll()
+           }
+
+           override suspend fun selectRaw(): List<RoomUser>? {
+               return dbDao.getUserAllSuspend()
+           }
+
+           override suspend fun upsert(entity: List<RoomUser>) {
+               return dbDao.replaceAll(*entity.toTypedArray())
+           }
+       }

+   private val allServerDaoWrapper: NoQuerySelectOnlyServerDaoWrapper = object :
+       NoQuerySelectOnlyServerDaoWrapper<List<RoomUser>> {
+       override suspend fun select(): List<RoomUser>? {
+           try {
+               return apiDao.getAllUser().map {
+                   toRoomUser(it)
+               }
+           } catch (t: Throwable) {
+               throw t
+           }
+       }
+   }
+
+   private fun toRoomUser(apiUser: ApiUser): RoomUser {
+       return RoomUser(
+           id = apiUser.id,
+           name = apiUser.name
+       )
+   }
    ...
```

Third, define `RepositoryHelper` object and operations you need.

```kotlin
class UserRepository(
    private val dbDao: UserDbDao,
    private val apiDao: UserApiDao
) {
    private val allCacheDaoWrapper: NoQueryCacheDaoWrapper = ...

    private val allServerDaoWrapper: NoQuerySelectOnlyServerDaoWrapper = ...

+   private val allHelper = RepositoryHelper.builder(
+       noQueryCacheDaoWrapper = allCacheDaoWrapper,
+       noQuerySelectOnlyServerDaoWrapper = allServerDaoWrapper,
+       onErrorReturn = { _, _ -> emptyList() }).build()

+   override fun getAllUser(): Flow<List<RoomUser>> = allHelper.select()

+   override suspend fun refreshUserAll() = allHelper.refresh()
}
```

Finally, use your repository operation!

```kotlin
    val users = userRepository.getAllUser().asLiveData(viewModelScope.coroutineContext + Dispatchers.IO).distinctUntilChanged()


```

## License

[Apache License 2.0](./LICENSE)
