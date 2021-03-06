(하기 내용은 개발 과정에서 발생했던 이슈를 해결하기 위해 개인적으로 고민했던 부분들을 적어 놓은 것으로, 개인적으로 보기 위해 작성하였음을 참고 부탁 드립니다)

## 유저 검색 성능 이슈

`README`의 **기능 개발 항목 - 유저 검색** 파트에서 작성한 바와 같이 `LIKE %Keyword%` 쿼리의 Explain 결과, `Full Table Scan`으로 조회하고 있음을 확인하였다. 유저 데이터 수가 십만 단위, 백만 단위를 넘기는 환경에서 풀 테이블 스캔의 성능은 이슈를 야기할 수 있기에, 이에 대한 해결책으로 MySQL의 `Full Text Search`를 도입하기로 결정했다.

### Full Text Search

**Full Text Index**

`Full Text Search`는 텍스트 전체를 인덱스화하여 특정 키워드가 포함된 레코드를 검색하는 방법을 말하며, 일반적인 용도의 B-Tree 인덱스는 사용할 수 없는 대신 `Full Text Search`를 위한 `Full Text Index`를 사용한다. MySQL에서 사용 가능한 FTS의 인덱싱 기법으로는 크게 `Stopword` 방식과 `N-gram` 방식이 있는데, 이중 `N-gram` 인덱싱 방식을 적용했다.

`Stopword` 방식은 전문의 내용을 공백이나 탭, 마침표 등의 문장 기호, 사용자가 정의한 문자열 등을 Stopword로 등록하고, Stopword를 기준으로 문서 내용을 단어 단위로 추출하여 인덱스를 생성한다. 이를 통해 검색 키워드와 일치하는 레코드를 조회할 수 있다. 다만 `Stopword` 방식은 추출한 키워드의 일부분만 검색하는 것은 불가능하다.

<br>

**N-Gram Indexing**

`N-gram` 방식은 띄어쓰기나 문장 기호 등에 구애 받지 않고, 일부 키워드만으로도 검색이 가능하도록 고안된 방법으로, 방식은 저장된 문서의 전문을 무조건적으로 특정 길이만큼 잘라서 인덱싱하는 방법이다. `Stopword` 방식에 비해 인덱싱 알고리즘이 복잡하고, 인덱스의 크기도 상당히 크다는 단점이 있으나 검색의 사용성이 훨씬 크기 때문에 대부분 `N-gram` 방식을 사용한다. `N-gram`은 일반적으로 2글자 단위(`tokenize`)로 키워드를 쪼개서 인덱싱하는 `2-gram(bi-gram)` 방식으로 많이 사용된다.

`2-gram` 인덱싱 기법은 2글자 단위의 최소 키워드에 대한 키를 관리하는 `Front-end` 인덱스와 2글자 이상의 키워드 묶음(n-SubSequence Window)을 관리하는 `Back-end` 인덱스 2개로 구성된다. 인덱스의 검색 과정은 인덱스 생성과는 반대로, 입력된 검색어를 2바이트 단위로 동일하게 자른 후 `Front-end` 인덱스를 검색한다. 그리고 그 결과를 대상 후보 군으로 선정한 뒤 `Back-end` 인덱스를 통해 최종 검증을 거쳐 일치하는 결과를 가져온다.

일부 키워드만 입력해도 검색 결과를 보여 주어야 하기에 `2-gram` 기법을 사용한 `Fulltext Search`를 적용하였다. `Fulltext Index`는 'user_id' 컬럼과 'nickname' 컬럼을 묶어서 생성하였다.

<br>

### Boolean Mode

`Boolean Mode`는 Fulltext Search 수행 시, 각 키워드의 포함, 불포함 비교를 수행하고 그 결과를 `TRUE/FALSE` 형태로 연산하여 최종 일치 여부를 판단하는 방식을 말한다. MySQL의 FTS Default Mode는 `Natural Language Mode`로서 이는 검색어에서 추출한 키워드가 각 키워드를 포함하는 레코드를 검색하는 방법으로, 토큰화된 키워드들이 얼마나 많이 포함되어 있는지에 따라 매치율이 결정된다.

`Natural Language Mode`는 검색어에서 추추한 키워드(토큰)들의 조회 결과를 합집합으로 구성하여 최종 결과를 도출하는 한편, `Boolean Mode`는 거기에 키워드(토큰)들의 Sequence까지 고려하여 최종 일치 여부를 검증한다.

MySQL의 Full Text Search는 다음과 같은 쿼리 문법을 사용한다.

```sql
SELECT *
FROM Member m
WHERE MATCH(m.user_id, m.nickname) AGAINST('keyword');
```

`MATCH`에 명시한 컬럼들에 대하여 `AGAINST`의 keyword로 Full Text Search를 한 결과를 가져오는 쿼리이다. 'user_id'와 'nickname'에 대한 다중 인덱스를 생성해 두었기 때문에 `MATCH` 안에 그에 해당하는 컬럼을 조건으로 주었다. 이렇게 검색을 했을 때 원하는 결과와 다른 결과를 가지고 왔습니다. 다음과 같은 상황을 예로 들 수 있다.

**'테스터' 라는 검색어로 Full Text Search 하는 경우**

1. 검색어 '테스터'는 '테스', '스터' 두 가지 키워드로 추출됨
2. '테스'라는 인덱스를 갖는 레코드와 '스터'라는 인덱스를 갖는 레코드를 조회
3. MySQL Natural Languge Mode의 매치 알고리즘에 의해 선별된 결과를 응답
4. '테스터'가 포함 된 레코드 외에, '테스형', '소크라테스', '갱스터', '힙스터' 등의 결과가 함께 조회

기존의 `LIKE %Keyword%` 방식과 조회 방법 자체가 다르기 때문에 완벽히 동일한 결과를 기대할 수는 없지만, `Boolean Mode`를 사용하면 적어도 '테스터'라는 단어가 온전히 들어간 결과들을 조회할 수 있을 것으로 기대하고 `Boolean Mode`를 적용했다. 또한, `2-gram` 인덱스를 사용하고 있기에 한 글자 입력 시에는 조회 결과를 보여줄 수 없었기 때문에 `Boolean Mode Syntax` 중 `*` 연산을 사용하였다. `*` 연산은 와일드카드 연산으로, 검색어가 n-gram token size 보다 작은 경우 검색 키워드로 시작하는 단어를 조회한다.

```sql
SELECT *
FROM Member m
WHERE MATCH(m.user_id, m.nickname) AGAINST('keyword' IN BOOLEAN MODE);
```

`Boolean Mode`를 적용하고 나니 원하는 결과를 조회할 수 있었고, 'Member' 테이블에 더미 데이터를 삽입하여 `LIKE %Keyword%` 쿼리와의 성능 비교 테스트를 진행하였다.

<br>

### 대용량 데이터에 대한 성능 비교 테스트

MySQL의 `Procedure`를 이용하여 더미 데이터를 삽입하며 테이블의 레코드 수에 따라 검색 성능이 어떠한 차이를 보이는지 테스트를 진행했다. 테스트는 데이터가 1만 개, 10만 개, 20만 개, 50만 개, 70만 개, 110만 개 일 때 LIKE 쿼리와 FTS 쿼리의 조회 속도를 측정하는 방식으로 진행하였다. 또한, 테스트는 각각 10회를 연속 조회한 뒤 초반 2회의 조회 결과를 제외한 8회 분의 결과이다. 즉, 3회차를 1회차로 간주하여 총 8회의 시행 결과를 얻었다. 테스트 결과는 아래와 같다.

**검색어: 'er57'**

**1. LIKE 쿼리 속도**

| LIKE %word% |  1만 개  |  10만 개  |  20만 개  |  50만 개  |  70만 개  | 110만 개  |
| :---------: | :------: | :-------: | :-------: | :-------: | :-------: | :-------: |
|    1회차    |   13ms   |   151ms   |   253ms   |   449ms   |   783ms   |   989ms   |
|    2회차    |   12ms   |   154ms   |   238ms   |   451ms   |   744ms   |   981ms   |
|    3회차    |   11ms   |   162ms   |   244ms   |   445ms   |   746ms   |   982ms   |
|    4회차    |   11ms   |   151ms   |   268ms   |   442ms   |   737ms   |   979ms   |
|    5회차    |   11ms   |   151ms   |   240ms   |   432ms   |   728ms   |   988ms   |
|    6회차    |   10ms   |   160ms   |   243ms   |   432ms   |   725ms   |   981ms   |
|    7회차    |   10ms   |   160ms   |   218ms   |   436ms   |   737ms   |   992ms   |
|    8회차    |   11ms   |   160ms   |   215ms   |   436ms   |   722ms   |   992ms   |
|  **평균**   | **11ms** | **155ms** | **240ms** | **440ms** | **740ms** | **986ms** |

**2. Fulltext Search 속도**

| Full Text Search |  1만 개  | 10만 개  |  20만 개  |  50만 개  |  70만 개  | 110만 개  |
| :--------------: | :------: | :------: | :-------: | :-------: | :-------: | :-------: |
|      1회차       |   15ms   |   95ms   |   180ms   |   193ms   |   197ms   |   199ms   |
|      2회차       |   15ms   |   89ms   |   177ms   |   199ms   |   192ms   |   213ms   |
|      3회차       |   16ms   |   88ms   |   184ms   |   195ms   |   193ms   |   197ms   |
|      4회차       |   14ms   |   88ms   |   186ms   |   191ms   |   193ms   |   201ms   |
|      5회차       |   13ms   |   99ms   |   182ms   |   186ms   |   193ms   |   199ms   |
|      6회차       |   14ms   |   99ms   |   180ms   |   189ms   |   197ms   |   202ms   |
|      7회차       |   13ms   |   99ms   |   179ms   |   184ms   |   200ms   |   196ms   |
|      8회차       |   13ms   |   99ms   |   188ms   |   182ms   |   193ms   |   199ms   |
|     **평균**     | **14ms** | **88ms** | **182ms** | **190ms** | **195ms** | **201ms** |

**3. LIKE 문 vs Fulltext Search**

|    검색 방법     |  1만 개  | 10만 개  |  20만 개  |  50만 개  |  70만 개  | 110만 개  |
| :--------------: | :------: | :------: | :-------: | :-------: | :-------: | :-------: |
|   LIKE %word%    |   11ms   |  155ms   |   240ms   |   440ms   |   740ms   |   986ms   |
| Full Text Search | **14ms** | **88ms** | **182ms** | **190ms** | **195ms** | **201ms** |

기대했던 대로 **테이블의 레코드 수가 많아질수록 Fulltext Search의 속도가 압도적으로 빠르다는 결과를 도출**할 수 있었다. 하나의 검색어에 대해 테이블 크기와 조회 속도의 연관성을 테스트하였으므로, **FTS가 검색어와 무관하게 일정한 성능을 유지할 수 있는지 검증을 시도**했다. 그러나 여기서 또 다른 이슈를 맞게 되었다.

<br>

### 검색 키워드에 따라 심하게 달라지는 성능 편차

앞선 테스트에서 `er57`이라는 키워드를 기준으로 성능테스트를 진행한 이유는 `er`이라는 키워드가 여러 곳(dummy_user####, fake_user####, faker####, tester#### 등)에서 사용되고 있었고, 더미 데이터의 userId와 nickname 뒷 부분에 넘버링을 하는 방식으로 삽입해주었기 때문에 적절한 테스트 대상이 될 수 있을 것으로 판단했기 때문이다. 그리고 공교롭게도 'er57'이라는 키워드에 대해서는 아무런 문제가 발생하지 않았다.

**그러나 어떤 키워드들에 대해서는 Full Text Search의 퍼포먼스가 현저히 떨어지는 현상이 발생**하였다. `faker`라는 키워드로 조회했을 때 결과를 응답하기까지의 시간은 `4000 ms`을 넘겼고, `chihiro` 또는 `neighbor` 등 **키워드 길이가 긴 검색어에 대해서는 분 단위를 넘어 아무리 기다려도 조회 결과를 응답하지 않는 현상**까지 발생한 것이다.
