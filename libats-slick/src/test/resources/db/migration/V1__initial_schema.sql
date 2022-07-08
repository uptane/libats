create table `books` (
  `id` INTEGER NOT NULL PRIMARY KEY,
  `title` VARCHAR(255) NOT NULL,
  `code` VARCHAR(255) NULL
)
;

create table `book_meta` (
  `book_id` INTEGER NOT NULL,
  `id` INTEGER NOT NULL,
  `tag` INTEGER NOT NULL,
  PRIMARY KEY (`book_id`, `id`),
  CONSTRAINT `book_id_fk` FOREIGN KEY (`book_id`) REFERENCES `books`(`id`)
)
;

CREATE TABLE `objects` (
  `namespace` varchar(254) COLLATE utf8_unicode_ci NOT NULL,
  `object_id` varchar(254) COLLATE utf8_unicode_ci NOT NULL,
  `status` enum('UPLOADED','CLIENT_UPLOADING','SERVER_UPLOADING') COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`namespace`,`object_id`),
  UNIQUE KEY `object_unique_namespace` (`namespace`,`object_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci
;

create table `re_key_spec`(
  `id` INTEGER NOT NULL PRIMARY KEY,
  `uuid` CHAR(36) NOT NULL UNIQUE,
  `encrypted_col` VARCHAR(255) NOT NULL
);
