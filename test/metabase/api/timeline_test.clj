(ns metabase.api.timeline-test
  "Tests for /api/timeline endpoints."
  (:require [clojure.test :refer :all]
            [metabase.http-client :as http]
            [metabase.models.collection :refer [Collection]]
            [metabase.models.timeline :refer [Timeline]]
            [metabase.models.timeline-event :refer [TimelineEvent]]
            [metabase.server.middleware.util :as middleware.u]
            [metabase.test :as mt]
            [metabase.util :as u]))

(deftest auth-tests
  (testing "Authentication"
    (is (= (get middleware.u/response-unauthentic :body)
           (http/client :get 401 "/timeline")))
    (is (= (get middleware.u/response-unauthentic :body)
           (http/client :get 401 "/timeline/1")))))

(deftest list-timelines-test
  (testing "GET /api/timeline"
    (mt/with-temp Collection [collection {:name "Important Data"}]
      (let [id          (u/the-id collection)
            tl-defaults {:creator_id    (u/the-id (mt/fetch-user :rasta))
                         :collection_id id}]
        (mt/with-temp* [Timeline [tl-a (merge tl-defaults {:name "Timeline A"})]
                        Timeline [tl-b (merge tl-defaults {:name "Timeline B"})]
                        Timeline [tl-c (merge tl-defaults {:name "Timeline C" :archived true})]]
          (testing "check that we only get un-archived timelines"
            (is (= (->> (mt/user-http-request :rasta :get 200 "timeline")
                        (filter #(= (:collection_id %) id))
                        (map :name)
                        set)
                   #{"Timeline A" "Timeline B"})))
          (testing "check that we only get archived timelines when `archived=true`"
            (is (= (->> (mt/user-http-request :rasta :get 200 "timeline" :archived true)
                        (filter #(= (:collection_id %) id))
                        (map :name)
                        set)
                   #{"Timeline C"}))))))))

(deftest get-timeline-test
  (testing "GET /api/timeline/:id"
    (let [tl-defaults {:creator_id    (u/the-id (mt/fetch-user :rasta))
                       :collection_id nil}]
      (mt/with-temp* [Timeline [tl-a (merge tl-defaults {:name "Timeline A"})]
                      Timeline [tl-b (merge tl-defaults {:name "Timeline B" :archived true})]]
        (testing "check that we get the timeline with the id specified"
          (is (= (->> (mt/user-http-request :rasta :get 200 (str "timeline/" (u/the-id tl-a)))
                      :name)
                 "Timeline A")))
        (testing "check that we get the timeline with the id specified, even if the timeline is archived"
          (is (= (->> (mt/user-http-request :rasta :get 200 (str "timeline/" (u/the-id tl-b)))
                      :name)
                 "Timeline B")))))))

(deftest create-timeline-test
  (testing "POST /api/timeline"
    (mt/with-temp Collection [collection {:name "Important Data"}]
      (let [id          (u/the-id collection)
            tl-defaults {:creator_id    (u/the-id (mt/fetch-user :rasta))
                         :collection_id id}]
        (testing "Create a new timeline"
          ;; make an API call to create a timeline
          (mt/user-http-request :rasta :post 200 "timeline" (merge tl-defaults {:name "Rasta's TL"}))
          ;; check the collection to see if the timeline is there
          (is (= (-> (db/select-one Timeline :collection_id id) :name)
                 "Rasta's TL")))))))

(deftest update-timeline-test
  (testing "PUT /api/timeline/:id"
    (mt/with-temp Collection [collection {:name "Important Data"}]
      (let [id          (u/the-id collection)
            tl-defaults {:creator_id    (u/the-id (mt/fetch-user :rasta))
                         :collection_id id}]
        (mt/with-temp* [Timeline [tl-a (merge tl-defaults {:name "Timeline A" :archived true})]]
          (testing "check that we successfully updated a timeline"
            (is (= (->> (mt/user-http-request :rasta :put 200 (str "timeline/" (u/the-id tl-a)) {:archived false})
                        :archived)
                   false))))))))

(defn- include-events-request
  [timeline archived?]
  (mt/user-http-request :rasta :get 200 (str "timeline/" (u/the-id timeline))
                        :include "events" :archived archived?))

(deftest timeline-hydration-test
  (testing "GET /api/timeline/:id?include=events"
    (mt/with-temp Collection [collection {:name "Important Data"}]
      (let [creator-id  (u/the-id (mt/fetch-user :rasta))
            tl-defaults {:creator_id    creator-id
                         :collection_id (u/the-id collection)}]
        (mt/with-temp* [Timeline      [empty-tl (merge tl-defaults {:name "Empty TL"})]
                        Timeline      [unarchived-tl (merge tl-defaults {:name "Un-archived Events TL"})]
                        Timeline      [archived-tl (merge tl-defaults {:name "Archived Events TL"})]
                        Timeline      [timeline (merge tl-defaults {:name "All Events TL"})]]
          (let [defaults {:creator_id   creator-id
                          :timestamp    (java.time.OffsetDateTime/now)
                          :timezone     "PST"
                          :time_matters false}]
            (mt/with-temp* [TimelineEvent [event-a (merge defaults {:name        "event-a"
                                                                    :timeline_id (u/the-id unarchived-tl)})]
                            TimelineEvent [event-b (merge defaults {:name        "event-b"
                                                                    :timeline_id (u/the-id unarchived-tl)})]
                            TimelineEvent [event-c (merge defaults {:name        "event-c"
                                                                    :timeline_id (u/the-id archived-tl)
                                                                    :archived    true})]
                            TimelineEvent [event-d (merge defaults {:name        "event-d"
                                                                    :timeline_id (u/the-id archived-tl)
                                                                    :archived    true})]
                            TimelineEvent [event-e (merge defaults {:name        "event-e"
                                                                    :timeline_id (u/the-id timeline)})]
                            TimelineEvent [event-f (merge defaults {:name        "event-f"
                                                                    :timeline_id (u/the-id timeline)
                                                                    :archived    true})]]
              (testing "check that a timeline with no events returns an empty list"
                (is (->> (include-events-request empty-tl false)
                         :events
                         (every-pred empty? seqable?))))
              (testing "check that a timeline with events returns those events"
                (is (->> (include-events-request unarchived-tl true)
                         :events
                         (every-pred empty? seqable?)))
                (is (= (->> (include-events-request unarchived-tl false)
                            :events
                            (map :name)
                            set)
                       #{"event-a" "event-b"})))
              (testing "check that a timeline with archived events returns those events only when `archived=true`"
                (is (->> (include-events-request archived-tl false)
                         :events
                         (every-pred empty? seqable?)))
                (is (= (->> (include-events-request archived-tl true)
                            :events
                            (map :name)
                            set)
                       #{"event-c" "event-d"})))
              (testing "check that a timeline with both (un-)archived events returns only events respecting the `archived` parameter"
                (is (= (->> (include-events-request timeline false)
                            :events
                            (map :name)
                            set)
                       #{"event-e"}))
                (is (= (->> (include-events-request timeline true)
                            :events
                            (map :name)
                            set)
                       #{"event-f"}))))))))))

;; TODO: Add timelines test to collection api tests + hydrating events + archived events
;; TODO: Add timelines test to card api tests + hydrating events + archived events
;; TODO: Add collection/card start/end time tests
;; TODO: Add timeline/timelineevent MODEL Tests?