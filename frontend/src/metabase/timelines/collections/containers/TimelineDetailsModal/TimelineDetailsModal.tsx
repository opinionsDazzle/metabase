import { connect } from "react-redux";
import { push } from "react-router-redux";
import _ from "underscore";
import * as Urls from "metabase/lib/urls";
import Timelines from "metabase/entities/timelines";
import TimelineEvents from "metabase/entities/timeline-events";
import { Collection, Timeline, TimelineEvent } from "metabase-types/api";
import { State } from "metabase-types/store";
import TimelineDetailsModal from "../../components/TimelineDetailsModal";
import LoadingAndErrorWrapper from "../../components/LoadingAndErrorWrapper";
import { ModalParams } from "../../types";

interface TimelineDetailsModalProps {
  params: ModalParams;
  timelines: Timeline[];
}

const timelineProps = {
  id: (state: State, props: TimelineDetailsModalProps) =>
    Urls.extractEntityId(props.params.timelineId),
  query: { include: "events" },
  LoadingAndErrorWrapper,
};

const timelinesProps = {
  query: (state: State, props: TimelineDetailsModalProps) => ({
    collectionId: Urls.extractCollectionId(props.params.slug),
    include: "events",
  }),
  LoadingAndErrorWrapper,
};

const mapStateToProps = (state: State, props: TimelineDetailsModalProps) => ({
  isOnlyTimeline: props.timelines.length <= 1,
});

const mapDispatchToProps = (dispatch: any) => ({
  onArchive: async (event: TimelineEvent) => {
    await dispatch(TimelineEvents.actions.setArchived(event, true));
  },
  onGoBack: (timeline: Timeline, collection: Collection) => {
    dispatch(push(Urls.timelinesInCollection(collection)));
  },
});

export default _.compose(
  Timelines.load(timelineProps),
  Timelines.loadList(timelinesProps),
  connect(mapStateToProps, mapDispatchToProps),
)(TimelineDetailsModal);
